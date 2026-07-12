package gg.modl.backend.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.ReplayRetentionSettings;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReplayRetentionSettingsService {
    private static final String SETTINGS_TYPE_REPLAY_RETENTION = "replayRetention";
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 365;

    private final ObjectMapper objectMapper;
    private final VersionedSettingsSupport<ReplayRetentionSettings> support;

    public ReplayRetentionSettingsService(SettingsDocumentService settingsDocumentService, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.support = VersionedSettingsSupport.of(
            settingsDocumentService, SETTINGS_TYPE_REPLAY_RETENTION, this::mapToSettings);
    }

    public ReplayRetentionSettings getReplayRetentionSettings(Server server) {
        return support.get(server);
    }

    public VersionedSettings<ReplayRetentionSettings> getReplayRetentionSettingsState(Server server) {
        return support.state(server);
    }

    public VersionedSettings<ReplayRetentionSettings> patchReplayRetentionSettings(
        Server server,
        long expectedVersion,
        Boolean enabled,
        Integer days
    ) {
        Map<String, Object> data = support.currentData(server);

        ReplayRetentionSettings merged = mapToSettings(data);
        if (enabled != null) {
            merged.setEnabled(enabled);
        }
        if (days != null) {
            merged.setDays(days);
        }

        ReplayRetentionSettings normalized = validateForWrite(merged);
        data.put("enabled", normalized.isEnabled());
        data.put("days", normalized.getDays());

        return support.save(server, expectedVersion, data);
    }

    private ReplayRetentionSettings mapToSettings(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return ReplayRetentionSettings.defaults();
        }

        Map<String, Object> merged = new LinkedHashMap<>();
        ReplayRetentionSettings defaults = ReplayRetentionSettings.defaults();
        merged.put("enabled", defaults.isEnabled());
        merged.put("days", defaults.getDays());
        merged.putAll(data);
        return normalizeForRead(codec().decode(merged));
    }

    private SettingsCodec<ReplayRetentionSettings> codec() {
        return SettingsCodec.of(objectMapper, ReplayRetentionSettings.class, ReplayRetentionSettings::defaults);
    }

    private ReplayRetentionSettings normalizeForRead(ReplayRetentionSettings settings) {
        ReplayRetentionSettings normalized = settings != null ? settings : ReplayRetentionSettings.defaults();
        if (normalized.getDays() < MIN_DAYS) {
            normalized.setDays(MIN_DAYS);
        } else if (normalized.getDays() > MAX_DAYS) {
            normalized.setDays(MAX_DAYS);
        }
        return normalized;
    }

    private ReplayRetentionSettings validateForWrite(ReplayRetentionSettings settings) {
        ReplayRetentionSettings normalized = settings != null ? settings : ReplayRetentionSettings.defaults();
        if (normalized.getDays() < MIN_DAYS) {
            if (normalized.isEnabled()) {
                throw new ValidationException("Replay retention days must be at least 1 when enabled");
            }
            normalized.setDays(MIN_DAYS);
        }
        if (normalized.getDays() > MAX_DAYS) {
            if (normalized.isEnabled()) {
                throw new ValidationException("Replay retention days must be at most 365");
            }
            normalized.setDays(MAX_DAYS);
        }
        return normalized;
    }
}
