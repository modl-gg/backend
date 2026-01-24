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

    @Indexed(name = "email_1", unique = true)
    private String email;

    @Indexed(name = "username_1", unique = true)
    private String username;

    private String role;

    @Indexed(name = "assignedMinecraftUuid_1", sparse = true)
    private String assignedMinecraftUuid;

    private String assignedMinecraftUsername;

    @Builder.Default
    private List<TicketSubscription> subscribedTickets = new ArrayList<>();

    private TicketSubscriptionSettings ticketSubscriptionSettings;

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
