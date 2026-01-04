package gg.modl.backend.ticket.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document
public class Ticket {
    @Id
    private String id;

    private String type;
    private String category;
    private String subject;
    private String status;

    private String creator;
    private String creatorUuid;
    private String creatorName;
    private String creatorAvatar;

    private String reportedPlayer;
    private String reportedPlayerUuid;

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Builder.Default
    private List<TicketReply> replies = new ArrayList<>();

    @Builder.Default
    private List<TicketNote> notes = new ArrayList<>();

    private List<Map<String, Object>> chatMessages;

    private Map<String, Object> formData;
    private Map<String, Object> data;

    private boolean locked;
    private String priority;
    private String assignedTo;

    private Date created;
    private Date updatedAt;
}
