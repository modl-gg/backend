package gg.modl.backend.ticket.data;

import gg.modl.backend.ai.data.AIAnalysisResult;
import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import gg.modl.backend.database.mongo.codegen.MongoFieldAlias;
import gg.modl.backend.database.mongo.codegen.MongoFieldAliases;
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
@GenerateMongoFields
@MongoFieldAliases({
        @MongoFieldAlias(name = "REPLY_NAME", path = "replies.name"),
        @MongoFieldAlias(name = "REPLY_CONTENT", path = "replies.content"),
        @MongoFieldAlias(name = "REPLY_CREATED", path = "replies.created"),
        @MongoFieldAlias(name = "REPLY_STAFF", path = "replies.staff")
})
public class Ticket {
    @Id
    private String id;

    private String type;
    private String category;
    private String subject;
    private String status;

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

    private List<ChatMessage> chatMessages;

    private Map<String, Object> formData;
    private Map<String, Object> data;

    private boolean locked;
    private String priority;

    @Builder.Default
    private List<String> assignedTo = new ArrayList<>();

    private Date created;
    private Date updatedAt;

    private AIAnalysisResult aiAnalysis;

    private boolean emailAuthEnabled;
    private boolean hidden;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String content;
        private Date timestamp;
    }
}
