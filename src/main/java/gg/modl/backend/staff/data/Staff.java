package gg.modl.backend.staff.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Document
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Staff {
    @Id
    private String id;

    @Indexed(name = "uidx_staff_email", unique = true)
    private String email;

    @Indexed(name = "uidx_staff_username", unique = true)
    private String username;

    private String role;

    @Indexed(name = "sidx_staff_assignedMinecraftUuid", sparse = true)
    private String assignedMinecraftUuid;

    private String assignedMinecraftUsername;

    @Builder.Default
    private String language = "en";

    @Builder.Default
    private String dateFormat = "MM/DD/YYYY";

    @Builder.Default
    private List<TicketSubscription> subscribedTickets = new ArrayList<>();

    private TicketSubscriptionSettings ticketSubscriptionSettings;

    // --- Staff 2FA ---

    /** Current pending 2FA verification token (cleared after verification). */
    private String twoFactorToken;

    /** IP address associated with the pending token. */
    private String twoFactorTokenIp;

    /** Timestamp (epoch millis) when the pending token was created. */
    private Long twoFactorTokenCreatedAt;

    /** True when a verification has completed but the plugin hasn't been notified yet. */
    @Builder.Default
    private boolean twoFactorPendingDelivery = false;

    /** IP address the session is bound to (set on verification). */
    private String twoFactorSessionIp;

    /** Epoch millis when the current 2FA session expires (7-day TTL set on verification). */
    private Long twoFactorSessionExpiresAt;

    private Date lastSeen;

    private Date createdAt;

    private Date updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TicketSubscription {
        private String ticketId;
        private Date subscribedAt;
        private Date lastReadAt;
        @Builder.Default
        private boolean active = true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TicketSubscriptionSettings {
        @Builder.Default
        private boolean enabled = true;
        private String frequency;
        private NotificationSettings emailNotifications;
        private NotificationSettings pushNotifications;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NotificationSettings {
        @Builder.Default
        private boolean enabled = false;
        @Builder.Default
        private boolean newTickets = false;
        @Builder.Default
        private boolean ticketReplies = false;
        @Builder.Default
        private boolean ticketStatusChanges = false;
        @Builder.Default
        private boolean ticketAssignments = false;
        private List<String> subscribedTypes;
    }
}
