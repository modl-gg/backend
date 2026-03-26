package gg.modl.backend.settings.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.database.mongo.repository.SettingsMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.service.ServerTimestampService;
import gg.modl.backend.settings.data.DefaultPunishmentTypes;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.data.Settings;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PunishmentTypeService extends AbstractSettingsService {
    private final ObjectMapper objectMapper;
    private final ServerTimestampService serverTimestampService;
    private static final String SETTINGS_TYPE_PUNISHMENT_TYPES = "punishmentTypes";

    public PunishmentTypeService(SettingsMongoRepository settingsRepository, ObjectMapper objectMapper, ServerTimestampService serverTimestampService) {
        super(settingsRepository);
        this.objectMapper = objectMapper;
        this.serverTimestampService = serverTimestampService;
    }

    public Optional<PunishmentType> getPunishmentTypeById(@NotNull Server server, int id) {
        return getPunishmentTypes(server).stream()
            .filter(pt -> pt.getId() == id)
            .findFirst();
    }

    public List<PunishmentType> getPunishmentTypes(@NotNull Server server) {
        Settings settings = findSettings(server, SETTINGS_TYPE_PUNISHMENT_TYPES).orElse(null);

        if (settings == null || settings.getData() == null) {
            return initializeDefaultTypes(server);
        }

        try {
            return objectMapper.convertValue(
                settings.getData(),
                new TypeReference<List<PunishmentType>>() {}
            );
        } catch (Exception e) {
            log.error("Failed to convert punishment types from settings, initializing defaults", e);
            return initializeDefaultTypes(server);
        }
    }

    public List<PunishmentType> initializeDefaultTypes(@NotNull Server server) {
        List<PunishmentType> defaultTypes = DefaultPunishmentTypes.getAll();
        return savePunishmentTypes(server, defaultTypes);
    }

    public List<PunishmentType> savePunishmentTypes(@NotNull Server server, @NotNull List<PunishmentType> types) {
        upsertListSettings(server, SETTINGS_TYPE_PUNISHMENT_TYPES, types);
        serverTimestampService.updatePunishmentTypesTimestamp(server);
        return types;
    }

    public PunishmentType updatePunishmentType(@NotNull Server server, int ordinal, @NotNull PunishmentType updatedType) {
        if (ordinal == 0 || ordinal == 5) {
            throw new IllegalArgumentException("Kick and Blacklist punishment types cannot be configured");
        }

        List<PunishmentType> types = getPunishmentTypes(server);

        for (int i = 0; i < types.size(); i++) {
            PunishmentType existing = types.get(i);
            if (existing.getOrdinal() == ordinal) {
                if (existing.isCustomizable()) {
                    // Custom type: full replacement, preserve ordinal and customizable flag
                    updatedType.setOrdinal(ordinal);
                    updatedType.setCustomizable(true);
                    types.set(i, updatedType);
                } else {
                    // Core type: only update configurable fields, preserve identity
                    existing.setStaffDescription(updatedType.getStaffDescription());
                    existing.setPlayerDescription(updatedType.getPlayerDescription());
                    existing.setAppealable(updatedType.getAppealable());
                    existing.setAppealForm(updatedType.getAppealForm());
                    existing.setDurations(updatedType.getDurations());
                    existing.setPoints(updatedType.getPoints());
                    existing.setSingleSeverityPunishment(updatedType.getSingleSeverityPunishment());
                    existing.setSingleSeverityDurations(updatedType.getSingleSeverityDurations());
                    existing.setSingleSeverityPoints(updatedType.getSingleSeverityPoints());
                    existing.setCanBeAltBlocking(updatedType.getCanBeAltBlocking());
                    existing.setCanBeStatWiping(updatedType.getCanBeStatWiping());
                    existing.setPermanentUntilSkinChange(updatedType.getPermanentUntilSkinChange());
                    existing.setPermanentUntilUsernameChange(updatedType.getPermanentUntilUsernameChange());
                    updatedType = existing;
                }
                break;
            }
        }

        savePunishmentTypes(server, types);
        return updatedType;
    }

    public boolean deletePunishmentType(@NotNull Server server, int ordinal) {
        if (ordinal < 6) {
            throw new IllegalArgumentException("Cannot delete core administrative punishment types");
        }

        List<PunishmentType> types = getPunishmentTypes(server);
        List<PunishmentType> filtered = types.stream()
            .filter(pt -> pt.getOrdinal() != ordinal)
            .toList();

        if (filtered.size() == types.size()) {
            return false;
        }

        savePunishmentTypes(server, new java.util.ArrayList<>(filtered));
        return true;
    }

    public PunishmentType createPunishmentType(@NotNull Server server, @NotNull PunishmentType newType) {
        List<PunishmentType> types = new java.util.ArrayList<>(getPunishmentTypes(server));

        int maxOrdinal = types.stream()
            .map(PunishmentType::getOrdinal)
            .filter(o -> o != null)
            .mapToInt(Integer::intValue)
            .max()
            .orElse(5);

        int maxId = types.stream()
            .map(PunishmentType::getId)
            .filter(i -> i != null)
            .mapToInt(Integer::intValue)
            .max()
            .orElse(5);

        newType.setOrdinal(maxOrdinal + 1);
        newType.setId(maxId + 1);
        newType.setCustomizable(true);

        types.add(newType);
        savePunishmentTypes(server, types);

        return newType;
    }

    public long calculateDurationMillis(
        @NotNull Server server,
        int ordinal,
        String severity,
        String offenseLevel
    ) {
        return getPunishmentTypeByOrdinal(server, ordinal)
            .map(type -> type.getDurationMillis(
                severity != null ? severity : "regular",
                offenseLevel != null ? offenseLevel : "first"
            ))
            .orElse(0L);
    }

    public Optional<PunishmentType> getPunishmentTypeByOrdinal(@NotNull Server server, int ordinal) {
        return getPunishmentTypes(server).stream()
            .filter(pt -> pt.getOrdinal() == ordinal)
            .findFirst();
    }

    public String getPunishmentTypeName(@NotNull Server server, int ordinal) {
        return getPunishmentTypeByOrdinal(server, ordinal)
            .map(PunishmentType::getName)
            .orElse("Unknown");
    }

    public boolean isAppealable(@NotNull Server server, int ordinal) {
        return getPunishmentTypeByOrdinal(server, ordinal)
            .map(PunishmentType::isAppealable)
            .orElse(false);
    }

    public int getPointsForPunishment(@NotNull Server server, int ordinal, String severity) {
        return getPunishmentTypeByOrdinal(server, ordinal)
            .map(type -> type.getPointsForSeverity(severity != null ? severity : "regular"))
            .orElse(0);
    }
}
