package gg.modl.backend.beta;

import gg.modl.backend.beta.data.BetaAudit;
import gg.modl.backend.database.mongo.repository.AuthSessionMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerAdminRepository;
import gg.modl.backend.database.mongo.repository.ServerBetaTesterRepository;
import gg.modl.backend.database.mongo.repository.ServerProvisioningRepository;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.email.EmailHTMLTemplate;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.infrastructure.util.PaginationHelper;
import gg.modl.backend.limits.ServerLimitPolicy;
import gg.modl.backend.registration.RegistrationService;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import gg.modl.backend.server.service.ServerProvisioningService;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBetaTesterService {
    private static final int DEFAULT_PAGE_LIMIT = 50;

    private final ServerBetaTesterRepository serverBetaTesterRepository;
    private final ServerAdminRepository serverAdminRepository;
    private final ServerProvisioningRepository serverProvisioningRepository;
    private final ServerService serverService;
    private final ServerProvisioningService provisioningService;
    private final RegistrationService registrationService;
    private final ServerLimitPolicy limitPolicy;
    private final AuthSessionMongoRepository authSessionRepository;
    private final BetaResetService betaResetService;
    private final BetaAuditService betaAuditService;
    private final SubdomainValidator subdomainValidator;
    private final EmailService emailService;

    public BetaTesterPage list(int page, int limit, String search) {
        int normalizedPage = PaginationHelper.normalizePage(page);
        int normalizedLimit = PaginationHelper.normalizeLimit(limit, DEFAULT_PAGE_LIMIT);
        int skip = PaginationHelper.calculateSkip(page, normalizedLimit);

        List<Server> servers = serverBetaTesterRepository.findBetaTesters(search, skip, normalizedLimit);
        long total = serverBetaTesterRepository.countBetaTesters(search);
        int pages = PaginationHelper.calculateTotalPages(total, normalizedLimit);

        List<BetaTesterDetails> items = servers.stream().map(this::details).toList();
        return new BetaTesterPage(items, normalizedPage, normalizedLimit, total, pages);
    }

    public BetaTesterDetails get(String id) {
        Server server = serverBetaTesterRepository.findById(id)
            .filter(candidate -> candidate.getBetaTesterCreatedAt() != null)
            .orElseThrow(() -> new BetaRequestException("Beta tester not found.", HttpStatus.NOT_FOUND));
        return details(server);
    }

    public BetaTesterDetails create(BetaTesterCreation creation, String actingAdminEmail) {
        String trimmedName = creation.serverName() == null ? null : creation.serverName().trim();
        if (trimmedName == null || trimmedName.isEmpty()) {
            throw new BetaRequestException("Server name is required.", HttpStatus.BAD_REQUEST);
        }

        String normalizedEmail = EmailAddressUtil.normalizeIfValid(creation.adminEmail());
        if (normalizedEmail == null) {
            throw new BetaRequestException("A valid admin email is required.", HttpStatus.BAD_REQUEST);
        }

        String subdomain = subdomainValidator.normalize(creation.customDomain());
        registrationService.validateIdentity(normalizedEmail, trimmedName, subdomain)
            .ifPresent(rejection -> {
                throw new BetaRequestException(rejection.message(), rejection.status());
            });

        Server saved = persistBetaServer(trimmedName, subdomain, normalizedEmail, actingAdminEmail);
        boolean tenantDatabaseExisted = provisioningService.tenantDatabaseExists(saved);

        try {
            provisioningService.provision(saved);
        } catch (Exception e) {
            serverAdminRepository.deleteByServerId(saved.getId());
            provisioningService.teardownProvisionedDatabase(saved, tenantDatabaseExisted);
            serverService.evictAllServerCaches();
            log.error("Beta provisioning failed for {}; rolled back the server row", subdomain, e);
            throw new BetaRequestException("Failed to provision beta tester panel. Please retry.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        registrationService.resolveOrGenerateApiKey(saved);
        serverProvisioningRepository.markProvisioningCompleted(saved.getId());
        serverService.evictAllServerCaches();

        Server reloaded = serverBetaTesterRepository.findById(saved.getId()).orElse(saved);
        betaAuditService.record(BetaAuditAction.CREATE, reloaded.getId(), actingAdminEmail,
            "Created beta tester panel " + subdomain);
        sendBetaReadyEmail(reloaded);
        return details(reloaded);
    }

    private void sendBetaReadyEmail(Server server) {
        try {
            String panelLink = registrationService.buildPanelUrl(server);
            emailService.send(server.getAdminEmail(),
                EmailHTMLTemplate.BETA_PANEL_READY.build(server.getServerName(), panelLink));
        } catch (Exception e) {
            log.error("Failed to send beta ready email for {}", server.getCustomDomain(), e);
        }
    }

    private Server persistBetaServer(String serverName, String subdomain, String adminEmail, String actingAdminEmail) {
        Date now = new Date();
        Server server = new Server(serverName, subdomain, serverService.generateDatabaseName(subdomain),
            adminEmail, true, ServerPlan.PREMIUM);
        server.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        server.setProvisioningStatus(ProvisioningStatus.IN_PROGRESS);
        server.setBetaTester(true);
        server.setBetaTesterCreatedAt(now);
        server.setBetaTesterCreatedBy(actingAdminEmail);
        server.setCreatedAt(now);
        server.setUpdatedAt(now);
        Server saved = serverBetaTesterRepository.saveEntity(server);
        serverService.evictAllServerCaches();
        return saved;
    }

    public BetaTesterDetails revoke(String id, String actingAdminEmail) {
        requireActiveBetaTester(id);

        Server updated = serverBetaTesterRepository.updateBetaState(id, ServerPlan.FREE, SubscriptionStatus.INACTIVE, false)
            .orElseThrow(() -> new BetaRequestException("Failed to revoke beta tester.", HttpStatus.INTERNAL_SERVER_ERROR));
        authSessionRepository.deleteAllForServer(updated);
        serverService.evictAllServerCaches();

        betaAuditService.record(BetaAuditAction.REVOKE, id, actingAdminEmail, "Revoked beta access");
        return details(updated);
    }

    public BetaResetResponse reset(String id, String actingAdminEmail) {
        Server server = requireActiveBetaTester(id);
        List<String> clearedCollections = betaResetService.reset(server);
        betaAuditService.record(BetaAuditAction.RESET, server.getId(), actingAdminEmail,
            "Reset cleared " + clearedCollections.size() + " collections");
        return new BetaResetResponse(server.getId(), clearedCollections);
    }

    public CompletableFuture<List<ResetResult>> resetAll(String actingAdminEmail) {
        return betaResetService.resetAll().thenApply(results -> {
            for (ResetResult result : results) {
                betaAuditService.record(BetaAuditAction.RESET_ALL, result.serverId(), actingAdminEmail,
                    result.success() ? "Reset all succeeded" : "Reset all failed: " + result.message());
            }
            return results;
        });
    }

    public List<BetaAudit> audit(String id, int limit) {
        return betaAuditService.findRecent(id, limit);
    }

    private Server requireActiveBetaTester(String id) {
        Server server = serverBetaTesterRepository.findById(id)
            .orElseThrow(() -> new BetaRequestException("Beta tester not found.", HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(server.getBetaTester())) {
            throw new BetaRequestException("Beta tester not found.", HttpStatus.NOT_FOUND);
        }
        return server;
    }

    private BetaTesterDetails details(Server server) {
        return new BetaTesterDetails(server, limitPolicy.resolve(server));
    }
}
