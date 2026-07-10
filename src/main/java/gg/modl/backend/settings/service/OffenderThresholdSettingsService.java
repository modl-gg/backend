package gg.modl.backend.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.OffenderThresholdSettings;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OffenderThresholdSettingsService {
    private final SettingsDocumentService settingsDocumentService;
    private final ObjectMapper objectMapper;
    private static final String SETTINGS_TYPE_STATUS_THRESHOLDS = "statusThresholds";

    private final Cache<String, OffenderThresholdSettings> thresholdCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(45))
        .maximumSize(500)
        .build();
    private static final int MIN_THRESHOLD = 0;
    private static final int MAX_THRESHOLD = 10_000;
    private static final int MIN_POINT_EXPIRY_MONTHS = 1;
    private static final int MAX_POINT_EXPIRY_MONTHS = 60;

    public OffenderThresholdSettings getThresholdSettings(Server server) {
        return thresholdCache.get(server.getId(), id -> getThresholdSettingsState(server).data());
    }

    public VersionedSettings<OffenderThresholdSettings> getThresholdSettingsState(Server server) {
        SettingsDocumentService.RawSettingsState state = settingsDocumentService.getRawState(server, SETTINGS_TYPE_STATUS_THRESHOLDS);
        OffenderThresholdSettings settings = mapToThresholdSettings(state.data());
        return new VersionedSettings<>(settings, state.version(), state.updatedAt());
    }

    public VersionedSettings<OffenderThresholdSettings> patchThresholdSettings(
        Server server,
        long expectedVersion,
        OffenderThresholdSettings patch
    ) {
        OffenderThresholdSettings current = getThresholdSettingsState(server).data();
        if (patch != null) {
            if (patch.getSocial() != null) {
                current.setSocial(sanitizeCategoryThresholds(patch.getSocial()));
            }
            if (patch.getGameplay() != null) {
                current.setGameplay(sanitizeCategoryThresholds(patch.getGameplay()));
            }
        }

        current = normalizeSettings(current);

        Map<String, Object> data = codec().encode(current);
        SettingsDocumentService.RawSettingsState updated;
        try {
            updated = settingsDocumentService.saveRawState(
                server,
                SETTINGS_TYPE_STATUS_THRESHOLDS,
                expectedVersion,
                new LinkedHashMap<>(data)
            );
        } finally {
            thresholdCache.invalidate(server.getId());
        }
        return new VersionedSettings<>(mapToThresholdSettings(updated.data()), updated.version(), updated.updatedAt());
    }

    public OffenderThresholdSettings updateThresholdSettings(Server server, OffenderThresholdSettings newSettings) {
        long expectedVersion = getThresholdSettingsState(server).version();
        return patchThresholdSettings(server, expectedVersion, newSettings).data();
    }

    private OffenderThresholdSettings mapToThresholdSettings(Map<String, Object> data) {
        OffenderThresholdSettings mapped = codec().decode(data);
        if (mapped.getSocial() == null || mapped.getGameplay() == null) {
            return OffenderThresholdSettings.defaults();
        }
        return normalizeSettings(mapped);
    }

    private SettingsCodec<OffenderThresholdSettings> codec() {
        return SettingsCodec.of(objectMapper, OffenderThresholdSettings.class, OffenderThresholdSettings::defaults);
    }

    private OffenderThresholdSettings normalizeSettings(OffenderThresholdSettings settings) {
        OffenderThresholdSettings normalized = settings != null ? settings : OffenderThresholdSettings.defaults();
        if (normalized.getSocial() == null) {
            normalized.setSocial(OffenderThresholdSettings.defaults().getSocial());
        }
        if (normalized.getGameplay() == null) {
            normalized.setGameplay(OffenderThresholdSettings.defaults().getGameplay());
        }

        normalized.setSocial(sanitizeCategoryThresholds(normalized.getSocial()));
        normalized.setGameplay(sanitizeCategoryThresholds(normalized.getGameplay()));
        return normalized;
    }

    private OffenderThresholdSettings.CategoryThresholds sanitizeCategoryThresholds(
        OffenderThresholdSettings.CategoryThresholds thresholds
    ) {
        int medium = sanitizeThresholdValue(thresholds.getMedium());
        int habitual = sanitizeThresholdValue(thresholds.getHabitual());
        int pointExpiryMonths = sanitizePointExpiryMonths(thresholds.getPointExpiryMonths());
        if (habitual < medium) {
            habitual = medium;
        }
        return new OffenderThresholdSettings.CategoryThresholds(medium, habitual, pointExpiryMonths);
    }

    private int sanitizeThresholdValue(int value) {
        return Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, value));
    }

    private int sanitizePointExpiryMonths(int value) {
        return Math.max(MIN_POINT_EXPIRY_MONTHS, Math.min(MAX_POINT_EXPIRY_MONTHS, value));
    }
}
