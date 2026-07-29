package gg.modl.backend.settings.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.service.ServerTimestampService;
import gg.modl.backend.settings.data.DefaultPunishmentTypes;
import gg.modl.backend.settings.data.PunishmentCategory;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.data.Settings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PunishmentTypeService {
    private final SettingsRepositoryAccess settingsRepositoryAccess;
    private final ObjectMapper objectMapper;
    private final ServerTimestampService serverTimestampService;
    private static final String SETTINGS_TYPE_PUNISHMENT_TYPES = "punishmentTypes";

    private final Cache<String, List<PunishmentType>> typesCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(45))
        .maximumSize(500)
        .build();

    public List<PunishmentType> getPunishmentTypes(@NotNull Server server) {
        return typesCache.get(server.getId(), id -> loadOrInitializeTypes(server));
    }

    private List<PunishmentType> loadOrInitializeTypes(@NotNull Server server) {
        Settings settings = settingsRepositoryAccess.findSettings(server, SETTINGS_TYPE_PUNISHMENT_TYPES).orElse(null);
        Object data = settings != null ? settings.getData() : null;
        return codec(server).decode(data);
    }

    private SettingsCodec<List<PunishmentType>> codec(@NotNull Server server) {
        return SettingsCodec.of(
            objectMapper,
            new TypeReference<List<PunishmentType>>() {},
            () -> persistPunishmentTypes(server, DefaultPunishmentTypes.getAll())
        );
    }

    public List<PunishmentType> initializeDefaultTypes(@NotNull Server server) {
        return savePunishmentTypes(server, DefaultPunishmentTypes.getAll());
    }

    public List<PunishmentType> savePunishmentTypes(@NotNull Server server, @NotNull List<PunishmentType> types) {
        try {
            return persistPunishmentTypes(server, types);
        } finally {
            typesCache.invalidate(server.getId());
        }
    }

    private List<PunishmentType> persistPunishmentTypes(@NotNull Server server, @NotNull List<PunishmentType> types) {
        settingsRepositoryAccess.upsertListSettings(server, SETTINGS_TYPE_PUNISHMENT_TYPES, types);
        serverTimestampService.updatePunishmentTypesTimestamp(server);
        return types;
    }

    public PunishmentType updatePunishmentType(@NotNull Server server, int ordinal, @NotNull PunishmentType updatedType) {
        if (ordinal == PunishmentCategory.MIN_CORE_ORDINAL || ordinal == PunishmentCategory.MAX_CORE_ORDINAL) {
            throw new IllegalArgumentException("Kick and Blacklist punishment types cannot be configured");
        }

        List<PunishmentType> types = new ArrayList<>(getPunishmentTypes(server));

        for (int i = 0; i < types.size(); i++) {
            PunishmentType existing = types.get(i);
            if (Objects.equals(existing.getOrdinal(), ordinal)) {
                if (existing.isCustomizable()) {
                    updatedType.setId(existing.getId());
                    updatedType.setOrdinal(ordinal);
                    updatedType.setCustomizable(true);
                    if (updatedType.getName() == null || updatedType.getName().isBlank()) {
                        updatedType.setName(existing.getName());
                    }
                    types.set(i, updatedType);
                } else {
                    PunishmentType merged = existing.toBuilder()
                        .staffDescription(updatedType.getStaffDescription())
                        .playerDescription(updatedType.getPlayerDescription())
                        .appealable(updatedType.getAppealable())
                        .appealForm(updatedType.getAppealForm())
                        .durations(updatedType.getDurations())
                        .points(updatedType.getPoints())
                        .singleSeverityPunishment(updatedType.getSingleSeverityPunishment())
                        .singleSeverityDurations(updatedType.getSingleSeverityDurations())
                        .singleSeverityPoints(updatedType.getSingleSeverityPoints())
                        .canBeAltBlocking(updatedType.getCanBeAltBlocking())
                        .canBeStatWiping(updatedType.getCanBeStatWiping())
                        .permanentUntilSkinChange(updatedType.getPermanentUntilSkinChange())
                        .permanentUntilUsernameChange(updatedType.getPermanentUntilUsernameChange())
                        .build();
                    types.set(i, merged);
                    updatedType = merged;
                }
                break;
            }
        }

        savePunishmentTypes(server, types);
        return updatedType;
    }

    public boolean deletePunishmentType(@NotNull Server server, int ordinal) {
        if (ordinal <= PunishmentCategory.MAX_CORE_ORDINAL) {
            throw new IllegalArgumentException("Cannot delete core administrative punishment types");
        }

        List<PunishmentType> types = getPunishmentTypes(server);
        List<PunishmentType> filtered = types.stream()
            .filter(pt -> !Objects.equals(pt.getOrdinal(), ordinal))
            .toList();

        if (filtered.size() == types.size()) {
            return false;
        }

        savePunishmentTypes(server, new ArrayList<>(filtered));
        return true;
    }

    public PunishmentType createPunishmentType(@NotNull Server server, @NotNull PunishmentType newType) {
        List<PunishmentType> types = new ArrayList<>(getPunishmentTypes(server));

        int maxOrdinal = types.stream()
            .map(PunishmentType::getOrdinal)
            .filter(o -> o != null)
            .mapToInt(Integer::intValue)
            .max()
            .orElse(5);

        int maxId = types.stream()
            .map(PunishmentType::getId)
            .filter(Objects::nonNull)
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
        return Optional.ofNullable(PunishmentTypeIndex.byOrdinal(getPunishmentTypes(server)).get(ordinal));
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
