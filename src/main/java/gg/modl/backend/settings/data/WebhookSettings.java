package gg.modl.backend.settings.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookSettings {
    private String discordWebhookUrl;
    private String discordAdminRoleId;
    private String botName;
    private String avatarUrl;
    private boolean enabled;
    private NotificationSettings notifications;
    private EmbedTemplates embedTemplates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSettings {
        private boolean newTickets;
        private boolean newPunishments;
        private boolean auditLogs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbedTemplates {
        private EmbedTemplate newTickets;
        private EmbedTemplate newPunishments;
        private EmbedTemplate auditLogs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbedTemplate {
        private String title;
        private String description;
        private String color;
        private List<EmbedField> fields;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbedField {
        private String name;
        private String value;
        private boolean inline;
    }
}
