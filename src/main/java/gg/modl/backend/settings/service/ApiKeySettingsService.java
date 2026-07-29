package gg.modl.backend.settings.service;

import gg.modl.backend.database.mongo.repository.ServerCredentialRepository;
import gg.modl.backend.database.mongo.repository.ServerLookupRepository;
import gg.modl.backend.infrastructure.scheduling.SchedulerLeaseService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.infrastructure.util.IdGenerator;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ApiKeySettingsService {
    private static final String SETTINGS_TYPE_API_KEYS = "apiKeys";
    private static final String API_KEY_FIELD = "api_key";
    private static final String API_KEY_BACKFILL_LEASE = "server-api-key-backfill";
    private static final Duration API_KEY_BACKFILL_LEASE_TTL = Duration.ofMinutes(30);

    private final IdGenerator idGenerator;
    private final ServerLookupRepository serverLookupRepository;
    private final ServerCredentialRepository serverCredentialRepository;
    private final SchedulerLeaseService schedulerLeaseService;
    private final VersionedSettingsSupport<Map<String, Object>> support;

    public ApiKeySettingsService(
        SettingsDocumentService settingsDocumentService,
        IdGenerator idGenerator,
        ServerLookupRepository serverLookupRepository,
        ServerCredentialRepository serverCredentialRepository,
        SchedulerLeaseService schedulerLeaseService
    ) {
        this.idGenerator = idGenerator;
        this.serverLookupRepository = serverLookupRepository;
        this.serverCredentialRepository = serverCredentialRepository;
        this.schedulerLeaseService = schedulerLeaseService;
        this.support = VersionedSettingsSupport.<Map<String, Object>>of(
            settingsDocumentService, SETTINGS_TYPE_API_KEYS, LinkedHashMap::new);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfillServerApiKeys() {
        if (!schedulerLeaseService.tryAcquire(API_KEY_BACKFILL_LEASE, API_KEY_BACKFILL_LEASE_TTL)) {
            return;
        }
        List<Server> servers;
        try {
            servers = serverLookupRepository.findAll();
        } catch (Exception e) {
            log.error("Failed to load servers for API key backfill", e);
            return;
        }
        int synced = 0;
        for (Server server : servers) {
            try {
                if (syncServerApiKeyFromSettings(server)) {
                    synced++;
                }
            } catch (Exception e) {
                log.warn("Failed to backfill Server.apiKey for server id={}", server.getId(), e);
            }
        }
        if (synced > 0) {
            log.info("Backfilled Server.apiKey from settings for {} tenant(s)", synced);
        }
    }

    private boolean syncServerApiKeyFromSettings(@NotNull Server server) {
        if (server.getDatabaseName() == null || server.getDatabaseName().isBlank()) {
            return false;
        }
        String settingsKey = getApiKeyFromSettings(server);
        if (settingsKey == null || settingsKey.isBlank()) {
            return false;
        }
        String currentKey = server.getApiKey();
        if (currentKey != null && currentKey.equals(settingsKey)) {
            return false;
        }
        serverCredentialRepository.updateApiKey(server.getId(), settingsKey);
        return true;
    }

    @Nullable
    public Server findServerByApiKey(@NotNull String apiKey) {
        return serverLookupRepository.findByApiKey(apiKey).orElse(null);
    }

    @Nullable
    public String getApiKeyFromSettings(@NotNull Server server) {
        Object apiKey = support.get(server).get(API_KEY_FIELD);
        return apiKey instanceof String value ? value : null;
    }

    public void syncApiKeyToServer(@NotNull Server server, @NotNull String apiKey) {
        serverCredentialRepository.updateApiKey(server.getId(), apiKey);
    }

    public String generateApiKey(Server server, String keyType) {
        VersionedSettings<Map<String, Object>> state = support.state(server);
        Map<String, Object> data = state.data();

        String newApiKey = generateSecureApiKey();
        String fieldName = getFieldNameForType(keyType);

        data.put(fieldName, newApiKey);
        support.save(server, state.version(), data);
        if (API_KEY_FIELD.equals(fieldName)) {
            syncApiKeyToServer(server, newApiKey);
            server.setApiKey(newApiKey);
        }

        return newApiKey;
    }

    private String generateSecureApiKey() {
        return "modl_" + idGenerator.generateToken();
    }

    private String getFieldNameForType(String keyType) {
        return switch (keyType.toLowerCase()) {
            case "ticket" -> "ticket_api_key";
            case "minecraft" -> "minecraft_api_key";
            default -> "api_key";
        };
    }

    public boolean deleteApiKey(Server server, String keyType) {
        VersionedSettings<Map<String, Object>> state = support.state(server);
        Map<String, Object> data = state.data();
        String fieldName = getFieldNameForType(keyType);

        if (!data.containsKey(fieldName)) {
            return false;
        }

        data.remove(fieldName);
        support.save(server, state.version(), data);
        if (API_KEY_FIELD.equals(fieldName)) {
            serverCredentialRepository.clearApiKey(server.getId());
            server.setApiKey(null);
        }

        return true;
    }

    public boolean hasApiKey(Server server, String keyType) {
        String apiKey = revealApiKey(server, keyType);
        return apiKey != null && !apiKey.isBlank();
    }

    public String revealApiKey(Server server, String keyType) {
        String fieldName = getFieldNameForType(keyType);
        Object apiKey = support.get(server).get(fieldName);
        return apiKey instanceof String value ? value : null;
    }
}
