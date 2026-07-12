package gg.modl.backend.audit.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.repository.AdminDatabaseBrowserRepository;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.server.data.Server;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDatabaseBrowserService {

    private final AdminDatabaseBrowserRepository databaseBrowserRepository;

    public static final Set<String> ALLOWED_TABLES = Set.of(
        CollectionName.PLAYERS,
        CollectionName.SETTINGS,
        CollectionName.STAFF,
        CollectionName.STAFF_ROLES,
        CollectionName.TICKETS,
        CollectionName.TICKET_VERIFICATIONS,
        CollectionName.LOGS,
        CollectionName.KNOWLEDGEBASE_CATEGORIES,
        CollectionName.KNOWLEDGEBASE_ARTICLES,
        CollectionName.HOMEPAGE_CARDS
    );

    private static final Set<String> SAFE_SETTINGS_TYPES = Set.of(
        "general", "punishmentTypes", "quickResponses", "replayRetention",
        "statusThresholds", "ticketForms", "ticketLabels");
    private static final List<String> SECRET_FIELD_NAMES = List.of(
        "api_key", "ticket_api_key", "minecraft_api_key", "apiKey", "webhookUrl", "token", "secret", "password");
    private static final String REDACTED = "[REDACTED]";

    public Map<String, Object> getDatabaseTable(
        Server server, String table, int limit, int skip) {
        if (!ALLOWED_TABLES.contains(table)) {
            throw new ValidationException("Invalid table name");
        }

        List<Document> documents = databaseBrowserRepository.readTable(server, table, limit, skip);
        long total = databaseBrowserRepository.countCollection(server, table);

        return Map.of(
            "data", redactDocuments(table, documents),
            "total", total,
            "limit", limit,
            "skip", skip
        );
    }

    private List<Document> redactDocuments(String table, List<Document> docs) {
        if (docs == null) {
            return Collections.emptyList();
        }
        List<Document> redacted = new ArrayList<>(docs.size());
        for (Document orig : docs) {
            Document copy = (Document) redactSecretFields(orig);
            if (CollectionName.SETTINGS.equals(table) && !SAFE_SETTINGS_TYPES.contains(copy.getString("type"))) {
                copy.put("data", REDACTED);
            }
            redacted.add(copy);
        }
        return redacted;
    }

    private Object redactSecretFields(Object value) {
        if (value instanceof Document document) {
            Document copy = new Document();
            for (Map.Entry<String, Object> entry : document.entrySet()) {
                copy.put(entry.getKey(),
                    isSecretFieldName(entry.getKey()) ? REDACTED : redactSecretFields(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(redactSecretFields(element));
            }
            return copy;
        }
        return value;
    }

    private boolean isSecretFieldName(String key) {
        String lowerKey = key.toLowerCase();
        return SECRET_FIELD_NAMES.stream().anyMatch(secret -> lowerKey.contains(secret.toLowerCase()));
    }
}
