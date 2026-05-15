package gg.modl.backend.appeal.dto.response;

import gg.modl.backend.ticket.dto.response.TicketResponse;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record PublicAppealResponse(
    String id,
    String _id,
    String type,
    String subject,
    String status,
    String appealWorkflowStatus,
    String creatorName,
    String creatorUuid,
    Date created,
    boolean locked,
    List<Map<String, Object>> replies,
    List<Map<String, Object>> messages,
    Map<String, Object> data,
    Map<String, Object> formData
) {
    public static PublicAppealResponse fromTicketResponse(TicketResponse appeal) {
        String creatorName = appeal.creatorName() != null ? appeal.creatorName() : "";
        String creatorUuid = appeal.creatorUuid() != null ? appeal.creatorUuid() : "";
        String workflowStatus = appeal.appealWorkflowStatus() != null ? appeal.appealWorkflowStatus() : appeal.status();
        List<Map<String, Object>> messages = filterPublicReplies(appeal);
        Map<String, Object> data = filterPublicData(appeal.data());
        Map<String, Object> formData = filterPublicData(appeal.formData());

        return new PublicAppealResponse(
            appeal.id(),
            appeal.id(),
            appeal.type(),
            appeal.subject(),
            workflowStatus,
            workflowStatus,
            creatorName,
            creatorUuid,
            appeal.date(),
            appeal.locked(),
            messages,
            messages,
            data,
            formData
        );
    }

    private static Map<String, Object> filterPublicData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> filtered = new HashMap<>(data);
        filtered.remove("contactEmail");
        filtered.remove("contact_email");
        filtered.remove("creatorEmail");
        filtered.remove("creatorIdentifier");
        filtered.remove("emailAuthEnabled");
        filtered.remove("email");
        filtered.remove("playerUuid");
        return filtered;
    }

    private static List<Map<String, Object>> filterPublicReplies(TicketResponse appeal) {
        if (appeal.messages() == null || appeal.messages().isEmpty()) {
            return Collections.emptyList();
        }
        return appeal.messages().stream().map(reply -> {
            Map<String, Object> publicReply = new HashMap<>();
            publicReply.put("id", reply.getId());
            publicReply.put("name", reply.getName());
            publicReply.put("avatar", reply.getAvatar());
            publicReply.put("content", reply.getContent());
            publicReply.put("type", reply.getType());
            publicReply.put("created", reply.getCreated());
            publicReply.put("staff", reply.isStaff());
            publicReply.put("action", reply.getAction());
            publicReply.put("attachments", reply.getAttachments() != null ? reply.getAttachments() : Collections.emptyList());
            return publicReply;
        }).toList();
    }
}
