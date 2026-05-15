package gg.modl.backend.appeal.dto.response;

import gg.modl.backend.ticket.dto.response.TicketResponse;
import java.util.Date;

public record PublicAppealResponse(
    String id,
    String type,
    String subject,
    String status,
    String appealWorkflowStatus,
    String creatorName,
    Date created,
    boolean locked
) {
    public static PublicAppealResponse fromTicketResponse(TicketResponse appeal) {
        String creatorName = appeal.creatorName() != null ? appeal.creatorName() : "";
        String workflowStatus = appeal.appealWorkflowStatus() != null ? appeal.appealWorkflowStatus() : appeal.status();

        return new PublicAppealResponse(
            appeal.id(),
            appeal.type(),
            appeal.subject(),
            workflowStatus,
            workflowStatus,
            creatorName,
            appeal.date(),
            appeal.locked()
        );
    }
}
