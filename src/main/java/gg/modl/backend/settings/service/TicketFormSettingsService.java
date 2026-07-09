package gg.modl.backend.settings.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.TicketFormSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketFormSettingsService {
    private final SettingsDocumentService settingsDocumentService;
    private final ObjectMapper objectMapper;
    private static final String SETTINGS_TYPE_TICKET_FORMS = "ticketForms";

    public VersionedSettings<TicketFormSettings> patchTicketFormSettings(
        Server server,
        long expectedVersion,
        TicketFormSettings newSettings
    ) {
        TicketFormSettings merged = newSettings != null ? newSettings : getDefaultTicketFormSettings();
        ensureFormDefaults(merged);
        Map<String, Object> data = codec().encode(merged);

        SettingsDocumentService.RawSettingsState updated = settingsDocumentService.saveRawState(
            server,
            SETTINGS_TYPE_TICKET_FORMS,
            expectedVersion,
            new LinkedHashMap<>(data)
        );

        return new VersionedSettings<>(mapToTicketFormSettings(updated.data()), updated.version(), updated.updatedAt());
    }

    public TicketFormSettings updateTicketFormSettings(Server server, TicketFormSettings newSettings) {
        long expectedVersion = getTicketFormSettingsState(server).version();
        return patchTicketFormSettings(server, expectedVersion, newSettings).data();
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

    public TicketFormSettings getTicketFormSettings(Server server) {
        return getTicketFormSettingsState(server).data();
    }

    public VersionedSettings<TicketFormSettings> getTicketFormSettingsState(Server server) {
        SettingsDocumentService.RawSettingsState state = settingsDocumentService.getRawState(server, SETTINGS_TYPE_TICKET_FORMS);
        TicketFormSettings settings = mapToTicketFormSettings(state.data());
        return new VersionedSettings<>(settings, state.version(), state.updatedAt());
    }

    private TicketFormSettings mapToTicketFormSettings(Map<String, Object> data) {
        TicketFormSettings mapped = codec().decode(data);
        ensureFormDefaults(mapped);
        return mapped;
    }

    private SettingsCodec<TicketFormSettings> codec() {
        return SettingsCodec.of(objectMapper, TicketFormSettings.class, this::getDefaultTicketFormSettings);
    }

    private void ensureFormDefaults(TicketFormSettings settings) {
        if (settings.getBug() == null) {
            settings.setBug(emptyForm());
        }
        if (settings.getSupport() == null) {
            settings.setSupport(emptyForm());
        }
        if (settings.getApplication() == null) {
            settings.setApplication(emptyForm());
        }
        if (settings.getPlayer() == null) {
            settings.setPlayer(emptyForm());
        }
        if (settings.getChat() == null) {
            settings.setChat(emptyForm());
        }

        for (TicketFormSettings.TicketForm form : List.of(
            settings.getBug(), settings.getSupport(), settings.getApplication(),
            settings.getPlayer(), settings.getChat()
        )) {
            sanitizeForm(form);
        }
    }

    private void sanitizeForm(TicketFormSettings.TicketForm form) {
        if (form.getFields() == null) {
            form.setFields(new ArrayList<>());
        }
        if (form.getSections() == null) {
            form.setSections(new ArrayList<>());
        }
        if (form.getAllowEmailNotifications() == null) {
            form.setAllowEmailNotifications(true);
        }
    }

    private TicketFormSettings getDefaultTicketFormSettings() {
        return TicketFormSettings.builder()
            .bug(emptyForm())
            .support(emptyForm())
            .application(emptyForm())
            .player(emptyForm())
            .chat(emptyForm())
            .build();
    }

    private TicketFormSettings.TicketForm emptyForm() {
        return TicketFormSettings.TicketForm.builder()
            .allowEmailNotifications(true)
            .fields(new ArrayList<>())
            .sections(new ArrayList<>())
            .build();
    }

    public TicketFormSettings updateFormByType(Server server, String formType, TicketFormSettings.TicketForm form) {
        TicketFormSettings settings = getTicketFormSettings(server);

        switch (formType.toLowerCase()) {
            case "bug" -> settings.setBug(form);
            case "support" -> settings.setSupport(form);
            case "application", "staff" -> settings.setApplication(form);
            case "player" -> settings.setPlayer(form);
            case "chat" -> settings.setChat(form);
            default -> {
                // no-op for unknown form type
            }
        }

        return updateTicketFormSettings(server, settings);
    }

    public Map<String, Object> buildTicketFormsResponse(TicketFormSettings ticketForms) {
        Map<String, Object> forms = new HashMap<>();
        putFormIfNotNull(forms, "bug", ticketForms.getBug());
        putFormIfNotNull(forms, "support", ticketForms.getSupport());
        putFormIfNotNull(forms, "application", ticketForms.getApplication());
        putFormIfNotNull(forms, "player", ticketForms.getPlayer());
        putFormIfNotNull(forms, "chat", ticketForms.getChat());
        return forms;
    }

    private void putFormIfNotNull(Map<String, Object> forms, String key, TicketFormSettings.TicketForm form) {
        if (form != null) {
            forms.put(key, objectMapper.convertValue(form, new TypeReference<Map<String, Object>>() {}));
        }
    }
}
