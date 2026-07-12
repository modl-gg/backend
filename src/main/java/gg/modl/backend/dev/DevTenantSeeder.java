package gg.modl.backend.dev;

import gg.modl.backend.database.mongo.repository.ServerLookupRepository;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.infrastructure.config.ModlDevProperties;
import gg.modl.backend.infrastructure.config.ModlProperties;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.service.ServerProvisioningService;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "modl.dev", name = "seed-tenant", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DevTenantSeeder implements ApplicationRunner {
    private final ModlProperties modlProperties;
    private final ModlDevProperties devProperties;
    private final ServerService serverService;
    private final ServerLookupRepository serverLookupRepository;
    private final ServerProvisioningService provisioningService;

    @Override
    public void run(ApplicationArguments args) {
        if (!modlProperties.isDevelopmentMode()) {
            log.warn("modl.dev.seed-tenant is enabled but development mode is off; skipping test tenant seeding.");
            return;
        }

        String serverDomain = devProperties.getServerDomain();
        if (serverDomain == null || serverDomain.isBlank()) {
            log.warn("modl.dev.seed-tenant is enabled but modl.dev.server-domain is blank; skipping test tenant seeding.");
            return;
        }

        String customDomain = resolveCustomDomain(serverDomain);
        String adminEmail = normalizeEmail(devProperties.getSeedAdminEmail());

        Server server = serverLookupRepository.findByCustomDomain(customDomain).orElse(null);
        if (server == null) {
            server = serverService.createServer(devProperties.getServerName(), customDomain, adminEmail, null, ServerPlan.PREMIUM);
            log.warn("[DevSeed] Created test tenant server '{}' (customDomain={})", devProperties.getServerName(), customDomain);
        }

        applyTestTenantDefaults(server, adminEmail);

        try {
            provisioningService.provision(server);
            server.setProvisioningStatus(ProvisioningStatus.COMPLETED);
            server.setProvisioningNotes(null);
        } catch (Exception e) {
            server.setProvisioningStatus(ProvisioningStatus.FAILED);
            log.error("[DevSeed] Provisioning failed for {}", customDomain, e);
        }

        server.setUpdatedAt(new Date());
        serverLookupRepository.saveEntity(server);
        serverService.evictAllServerCaches();

        log.warn("======================================================================");
        log.warn("  DEV TEST TENANT READY");
        log.warn("  Panel domain : {}", serverDomain);
        log.warn("  Super-admin  : {}", adminEmail);
        log.warn("  Login: request a code for the email above; it prints to this console.");
        log.warn("  Provisioning : {}", server.getProvisioningStatus());
        log.warn("======================================================================");
    }

    private void applyTestTenantDefaults(Server server, String adminEmail) {
        server.setEmailVerified(true);
        server.setPlan(ServerPlan.PREMIUM);

        if (adminEmail != null && !adminEmail.equalsIgnoreCase(server.getAdminEmail())) {
            server.setAdminEmail(adminEmail);
        }

        String seedApiKey = devProperties.getSeedApiKey();
        if (seedApiKey != null && !seedApiKey.isBlank() && (server.getApiKey() == null || server.getApiKey().isBlank())) {
            server.setApiKey(seedApiKey);
        }
    }

    private String normalizeEmail(String email) {
        String normalized = EmailAddressUtil.normalize(email);
        return normalized != null ? normalized : email;
    }

    private String resolveCustomDomain(String serverDomain) {
        String appDomain = serverService.getAppDomain(serverDomain);
        if (appDomain != null) {
            return serverDomain.substring(0, serverDomain.length() - appDomain.length() - 1);
        }
        return serverDomain;
    }
}
