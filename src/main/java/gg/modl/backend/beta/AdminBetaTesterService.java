package gg.modl.backend.beta;

import gg.modl.backend.beta.data.BetaAudit;
import gg.modl.backend.database.mongo.repository.AuthSessionMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.email.EmailAddressUtil;
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

    private final ServerMongoRepository serverRepository;
    private final ServerService serverService;
    private final ServerProvisioningService provisioningService;
    private final RegistrationService registrationService;
    private final ServerLimitPolicy limitPolicy;
    private final AuthSessionMongoRepository authSessionRepository;
    private final BetaResetService betaResetService;
    private final BetaAuditService betaAuditService;
    private final SubdomainValidator subdomainValidator;

    public BetaTesterListResponse list(int page, int limit, String search) {
        int normalizedPage = PaginationHelper.normalizePage(page);
        int normalizedLimit = PaginationHelper.normalizeLimit(limit, DEFAULT_PAGE_LIMIT);
        int skip = PaginationHelper.calculateSkip(page, normalizedLimit);

        List<Server> servers = serverRepository.findBetaTesters(search, skip, normalizedLimit);
        long total = serverRepository.countBetaTesters(search);
        int pages = PaginationHelper.calculateTotalPages(total, normalizedLimit);

        List<BetaTesterRecord> records = servers.stream().map(this::toRecord).toList();
        return new BetaTesterListResponse(records,
            new BetaTesterListResponse.Pagination(normalizedPage, normalizedLimit, total, pages));
    }

    public BetaTesterRecord get(String id) {
        Server server = serverRepository.findById(id)
            .filter(candidate -> candidate.getBetaTesterCreatedAt() != null)
            .orElseThrow(() -> new BetaRequestException("Beta tester not found.", HttpStatus.NOT_FOUND));
        return toRecord(server);
    }

    public BetaTesterRecord create(String serverName, String customDomain, String adminEmail, String actingAdminEmail) {
        String trimmedName = serverName == null ? null : serverName.trim();
        if (trimmedName == null || trimmedName.isEmpty()) {
            throw new BetaRequestException("Server name is required.", HttpStatus.BAD_REQUEST);
        }

        subdomainValidator.validate(customDomain).ifPresent(message -> {
            throw new BetaRequestException(message, HttpStatus.BAD_REQUEST);
        });
        String subdomain = subdomainValidator.normalize(customDomain);

        String normalizedEmail = EmailAddressUtil.normalizeIfValid(adminEmail);
        if (normalizedEmail == null) {
            throw new BetaRequestException("A valid admin email is required.", HttpStatus.BAD_REQUEST);
        }

        if (serverRepository.findByCustomDomain(subdomain).isPresent()) {
            throw new BetaRequestException("This subdomain is already in use.", HttpStatus.CONFLICT);
        }
        ServerService.ServerExistResult exist = serverService.doesServerExist(normalizedEmail, trimmedName, subdomain);
        if (exist.emailMatch()) {
            throw new BetaRequestException("An account with this email already exists.", HttpStatus.CONFLICT);
        }
        if (exist.nameMatch()) {
            throw new BetaRequestException("This server name is already taken.", HttpStatus.CONFLICT);
        }

        Server saved = persistBetaServer(trimmedName, subdomain, normalizedEmail, actingAdminEmail);

        try {
            provisioningService.provision(saved);
        } catch (Exception e) {
            serverRepository.deleteByServerId(saved.getId());
            serverService.evictAllServerCaches();
            log.error("Beta provisioning failed for {}; rolled back the server row", subdomain, e);
            throw new BetaRequestException("Failed to provision beta tester panel. Please retry.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        registrationService.resolveOrGenerateApiKey(saved);
        serverRepository.markProvisioningCompleted(saved.getId());
        serverService.evictAllServerCaches();

        Server reloaded = serverRepository.findById(saved.getId()).orElse(saved);
        betaAuditService.record(BetaAuditAction.CREATE, reloaded.getId(), actingAdminEmail,
            "Created beta tester panel " + subdomain);
        return toRecord(reloaded);
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
        Server saved = serverRepository.saveEntity(server);
        serverService.evictAllServerCaches();
        return saved;
    }

    public BetaTesterRecord revoke(String id, String actingAdminEmail) {
        requireActiveBetaTester(id);

        Server updated = serverRepository.updateBetaState(id, ServerPlan.FREE, SubscriptionStatus.INACTIVE, false)
            .orElseThrow(() -> new BetaRequestException("Failed to revoke beta tester.", HttpStatus.INTERNAL_SERVER_ERROR));
        authSessionRepository.deleteAllForServer(updated);
        serverService.evictAllServerCaches();

        betaAuditService.record(BetaAuditAction.REVOKE, id, actingAdminEmail, "Revoked beta access");
        return toRecord(updated);
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

    public BetaAuditResponse audit(String id, int limit) {
        List<BetaAudit> entries = betaAuditService.findRecent(id, limit);
        List<BetaAuditResponse.Entry> mapped = entries.stream()
            .map(entry -> new BetaAuditResponse.Entry(
                entry.getAction(),
                entry.getAdminEmail(),
                entry.getTimestamp() != null ? entry.getTimestamp().toInstant() : null,
                entry.getDetails()))
            .toList();
        return new BetaAuditResponse(mapped);
    }

    private Server requireActiveBetaTester(String id) {
        Server server = serverRepository.findById(id)
            .orElseThrow(() -> new BetaRequestException("Beta tester not found.", HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(server.getBetaTester())) {
            throw new BetaRequestException("Beta tester not found.", HttpStatus.NOT_FOUND);
        }
        return server;
    }

    private BetaTesterRecord toRecord(Server server) {
        return BetaTesterRecord.from(server, limitPolicy.resolve(server));
    }
}
