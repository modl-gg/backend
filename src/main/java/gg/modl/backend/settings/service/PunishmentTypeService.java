package gg.modl.backend.settings.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.service.ServerTimestampService;
import gg.modl.backend.settings.data.DefaultPunishmentTypes;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.data.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PunishmentTypeService {
    private static final String SETTINGS_TYPE_PUNISHMENT_TYPES = "punishmentTypes";

    private final DynamicMongoTemplateProvider mongoProvider;
    private final ObjectMapper objectMapper;
    private final ServerTimestampService serverTimestampService;

    public List<PunishmentType> getPunishmentTypes(@NotNull Server server) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_PUNISHMENT_TYPES));
        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);

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

    public Optional<PunishmentType> getPunishmentTypeByOrdinal(@NotNull Server server, int ordinal) {
        return getPunishmentTypes(server).stream()
                .filter(pt -> pt.getOrdinal() == ordinal)
                .findFirst();
    }

    public Optional<PunishmentType> getPunishmentTypeById(@NotNull Server server, int id) {
        return getPunishmentTypes(server).stream()
                .filter(pt -> pt.getId() == id)
                .findFirst();
    }

    public List<PunishmentType> savePunishmentTypes(@NotNull Server server, @NotNull List<PunishmentType> types) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_PUNISHMENT_TYPES));
        Update update = new Update()
                .set("type", SETTINGS_TYPE_PUNISHMENT_TYPES)
                .set("data", types);

        template.upsert(query, update, Settings.class, CollectionName.SETTINGS);
        serverTimestampService.updatePunishmentTypesTimestamp(server);
        return types;
    }

    public PunishmentType updatePunishmentType(@NotNull Server server, int ordinal, @NotNull PunishmentType updatedType) {
        List<PunishmentType> types = getPunishmentTypes(server);

        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).getOrdinal() == ordinal) {
                if (!types.get(i).isCustomizable()) {
                    throw new IllegalArgumentException("Administrative punishment types cannot be customized");
                }
                updatedType.setOrdinal(ordinal);
                updatedType.setCustomizable(true);
                types.set(i, updatedType);
                break;
            }
        }

        savePunishmentTypes(server, types);
        return updatedType;
    }

    public List<PunishmentType> initializeDefaultTypes(@NotNull Server server) {
        List<PunishmentType> defaultTypes = DefaultPunishmentTypes.getAll();
        return savePunishmentTypes(server, defaultTypes);
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
