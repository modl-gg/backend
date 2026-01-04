package gg.modl.backend.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.Settings;
import gg.modl.backend.settings.data.TicketFormSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketFormSettingsService {
    private static final String SETTINGS_TYPE_TICKET_FORMS = "ticketForms";

    private final DynamicMongoTemplateProvider mongoProvider;
    private final ObjectMapper objectMapper;

    public TicketFormSettings getTicketFormSettings(Server server) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_TICKET_FORMS));
        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);

        if (settings == null || settings.getData() == null) {
            return getDefaultTicketFormSettings();
        }

        try {
            return objectMapper.convertValue(settings.getData(), TicketFormSettings.class);
        } catch (Exception e) {
            log.error("Error converting ticket form settings", e);
            return getDefaultTicketFormSettings();
        }
    }

    public TicketFormSettings updateTicketFormSettings(Server server, TicketFormSettings newSettings) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_TICKET_FORMS));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.convertValue(newSettings, Map.class);

        Update update = new Update()
                .set("type", SETTINGS_TYPE_TICKET_FORMS)
                .set("data", data);

        template.upsert(query, update, Settings.class, CollectionName.SETTINGS);

        return getTicketFormSettings(server);
    }

    public TicketFormSettings.TicketForm getFormByType(Server server, String formType) {
        TicketFormSettings settings = getTicketFormSettings(server);

        return switch (formType.toLowerCase()) {
            case "bug" -> settings.getBug();
            case "support" -> settings.getSupport();
            case "application", "staff" -> settings.getApplication();
            case "player" -> settings.getPlayer();
            case "chat" -> settings.getChat();
            default -> null;
        };
    }

    public TicketFormSettings updateFormByType(Server server, String formType, TicketFormSettings.TicketForm form) {
        TicketFormSettings settings = getTicketFormSettings(server);

        switch (formType.toLowerCase()) {
            case "bug" -> settings.setBug(form);
            case "support" -> settings.setSupport(form);
            case "application", "staff" -> settings.setApplication(form);
            case "player" -> settings.setPlayer(form);
            case "chat" -> settings.setChat(form);
        }

        return updateTicketFormSettings(server, settings);
    }

    private TicketFormSettings getDefaultTicketFormSettings() {
        TicketFormSettings.TicketForm emptyForm = TicketFormSettings.TicketForm.builder()
                .fields(new ArrayList<>())
                .sections(new ArrayList<>())
                .build();

        return TicketFormSettings.builder()
                .bug(emptyForm)
                .support(emptyForm)
                .application(emptyForm)
                .player(emptyForm)
                .chat(emptyForm)
                .build();
    }
}
