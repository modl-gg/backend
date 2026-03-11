package gg.modl.backend.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.database.mongo.repository.SettingsMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.Settings;
import gg.modl.backend.settings.data.WebhookSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class WebhookSettingsService extends AbstractSettingsService {
    private static final String SETTINGS_TYPE_WEBHOOKS = "webhookSettings";

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public WebhookSettingsService(SettingsMongoRepository settingsRepository, ObjectMapper objectMapper, RestTemplate restTemplate) {
        super(settingsRepository);
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public WebhookSettings getWebhookSettings(Server server) {
        Settings settings = findSettings(server, SETTINGS_TYPE_WEBHOOKS).orElse(null);

        if (settings == null || settings.getData() == null) {
            return getDefaultWebhookSettings();
        }

        try {
            return objectMapper.convertValue(settings.getData(), WebhookSettings.class);
        } catch (Exception e) {
            log.error("Error converting webhook settings", e);
            return getDefaultWebhookSettings();
        }
    }

    public WebhookSettings updateWebhookSettings(Server server, WebhookSettings newSettings) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.convertValue(newSettings, Map.class);

        upsertSettings(server, SETTINGS_TYPE_WEBHOOKS, data);

        return getWebhookSettings(server);
    }

    public boolean testWebhook(Server server) {
        WebhookSettings settings = getWebhookSettings(server);
        String webhookUrl = settings.getDiscordWebhookUrl();

        if (webhookUrl == null || webhookUrl.isBlank()) {
            return false;
        }

        if (!isAllowedDiscordWebhookUrl(webhookUrl)) {
            log.warn("Rejected webhook test URL due to failed validation");
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = Map.of(
                    "username", settings.getBotName() != null ? settings.getBotName() : "modl Panel",
                    "avatar_url", settings.getAvatarUrl() != null ? settings.getAvatarUrl() : "",
                    "embeds", List.of(Map.of(
                            "title", "Webhook Test",
                            "description", "This is a test message from modl Panel to verify your webhook is working correctly.",
                            "color", 3447003
                    ))
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(webhookUrl, request, String.class);

            return true;
        } catch (Exception e) {
            log.error("Webhook test failed", e);
            return false;
        }
    }

    private boolean isAllowedDiscordWebhookUrl(String webhookUrl) {
        try {
            URI uri = URI.create(webhookUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }

            String host = uri.getHost();
            if (host == null) {
                return false;
            }

            boolean allowedHost = "discord.com".equalsIgnoreCase(host) || "discordapp.com".equalsIgnoreCase(host);
            if (!allowedHost) {
                return false;
            }

            String path = uri.getPath();
            return path != null && path.startsWith("/api/webhooks/");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private WebhookSettings getDefaultWebhookSettings() {
        return WebhookSettings.builder()
                .discordWebhookUrl("")
                .discordAdminRoleId("")
                .botName("modl Panel")
                .avatarUrl("")
                .enabled(false)
                .notifications(WebhookSettings.NotificationSettings.builder()
                        .newTickets(true)
                        .newPunishments(true)
                        .auditLogs(false)
                        .build())
                .embedTemplates(WebhookSettings.EmbedTemplates.builder()
                        .newTickets(createDefaultTicketEmbed())
                        .newPunishments(createDefaultPunishmentEmbed())
                        .auditLogs(createDefaultAuditEmbed())
                        .build())
                .build();
    }

    private WebhookSettings.EmbedTemplate createDefaultTicketEmbed() {
        return WebhookSettings.EmbedTemplate.builder()
                .title("New Ticket Created")
                .description("A new **{{type}}** ticket has been submitted.")
                .color("#3498db")
                .fields(List.of(
                        new WebhookSettings.EmbedField("Ticket ID", "#{{id}}", true),
                        new WebhookSettings.EmbedField("Priority", "{{priority}}", true),
                        new WebhookSettings.EmbedField("Category", "{{category}}", true),
                        new WebhookSettings.EmbedField("Subject", "{{title}}", false),
                        new WebhookSettings.EmbedField("Submitted By", "{{submittedBy}}", true)
                ))
                .build();
    }

    private WebhookSettings.EmbedTemplate createDefaultPunishmentEmbed() {
        return WebhookSettings.EmbedTemplate.builder()
                .title("New Punishment Issued")
                .description("A new **{{type}}** punishment has been issued.")
                .color("#e74c3c")
                .fields(List.of(
                        new WebhookSettings.EmbedField("Player", "{{playerName}}", true),
                        new WebhookSettings.EmbedField("Punishment Type", "{{type}}", true),
                        new WebhookSettings.EmbedField("Severity", "{{severity}}", true),
                        new WebhookSettings.EmbedField("Duration", "{{duration}}", true),
                        new WebhookSettings.EmbedField("Reason", "{{reason}}", false),
                        new WebhookSettings.EmbedField("Issued By", "{{issuer}}", true)
                ))
                .build();
    }

    private WebhookSettings.EmbedTemplate createDefaultAuditEmbed() {
        return WebhookSettings.EmbedTemplate.builder()
                .title("Audit Log Entry")
                .description("A new audit log entry has been recorded.")
                .color("#f39c12")
                .fields(List.of(
                        new WebhookSettings.EmbedField("User", "{{user}}", true),
                        new WebhookSettings.EmbedField("Action", "{{action}}", true),
                        new WebhookSettings.EmbedField("Target", "{{target}}", true),
                        new WebhookSettings.EmbedField("Details", "{{details}}", false)
                ))
                .build();
    }
}
