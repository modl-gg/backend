package gg.modl.backend.appeal.dto.response;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.modl.backend.ticket.dto.response.TicketResponse;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublicAppealResponseTest {

    @Test
    void fromTicketResponseUsesWorkflowStatusForPublicStatusField() {
        TicketResponse ticketResponse = new TicketResponse(
            "APPEAL-123",
            "appeal",
            "Ban Appeal",
            "Appeal subject",
            "closed",
            "rejected",
            "PlayerOne",
            "uuid-1",
            "PlayerOne",
            null,
            null,
            new Date(),
            true,
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            null,
            null,
            false,
            false,
            null
        );

        PublicAppealResponse publicAppealResponse = PublicAppealResponse.fromTicketResponse(ticketResponse);

        assertEquals("rejected", publicAppealResponse.status());
        assertEquals("rejected", publicAppealResponse.appealWorkflowStatus());
    }
}
