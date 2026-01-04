package gg.modl.backend.staff.data;

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
public class Staff {
    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    @Indexed(unique = true)
    private String username;

    private String role;

    @Indexed(sparse = true)
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
    public static class TicketSubscriptionSettings {
        @Builder.Default
        private boolean autoSubscribe = true;
        @Builder.Default
        private boolean emailNotifications = false;
    }
}
