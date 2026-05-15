package gg.modl.backend.appeal.dto.response;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.modl.backend.ticket.data.TicketReply;
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
            List.of(TicketReply.builder().id("reply-1").content("appeal body").creatorIdentifier("browser-secret").build()),
            List.of(),
            List.of(),
            Map.of(
                "form", "value",
                "email", "player@example.com",
                "contact_email", "legacy@example.com",
                "contactEmail", "player@example.com",
                "creatorEmail", "player@example.com",
                "creatorIdentifier", "browser-secret",
                "emailAuthEnabled", true,
                "playerUuid", "uuid-1"
            ),
            Map.of(
                "extra", "value",
                "contactEmail", "player@example.com",
                "email", "player@example.com",
                "contact_email", "legacy@example.com",
                "emailAuthEnabled", true,
                "playerUuid", "uuid-1"
            ),
            null,
            null,
            false,
            false,
            null,
            List.of()
        );

        PublicAppealResponse publicAppealResponse = PublicAppealResponse.fromTicketResponse(ticketResponse);

        assertEquals("rejected", publicAppealResponse.status());
        assertEquals("rejected", publicAppealResponse.appealWorkflowStatus());
        assertEquals("uuid-1", publicAppealResponse.creatorUuid());
        assertEquals("appeal body", publicAppealResponse.messages().get(0).get("content"));
        assertEquals("appeal body", publicAppealResponse.replies().get(0).get("content"));
        assertEquals(null, publicAppealResponse.messages().get(0).get("creatorIdentifier"));
        assertEquals("value", publicAppealResponse.data().get("extra"));
        assertEquals(null, publicAppealResponse.data().get("contactEmail"));
        assertEquals(null, publicAppealResponse.data().get("email"));
        assertEquals(null, publicAppealResponse.data().get("contact_email"));
        assertEquals(null, publicAppealResponse.data().get("emailAuthEnabled"));
        assertEquals(null, publicAppealResponse.data().get("playerUuid"));
        assertEquals("value", publicAppealResponse.formData().get("form"));
        assertEquals(null, publicAppealResponse.formData().get("email"));
        assertEquals(null, publicAppealResponse.formData().get("contact_email"));
        assertEquals(null, publicAppealResponse.formData().get("contactEmail"));
        assertEquals(null, publicAppealResponse.formData().get("creatorEmail"));
        assertEquals(null, publicAppealResponse.formData().get("creatorIdentifier"));
        assertEquals(null, publicAppealResponse.formData().get("emailAuthEnabled"));
        assertEquals(null, publicAppealResponse.formData().get("playerUuid"));
    }
}
