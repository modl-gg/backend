package gg.modl.backend.ticket.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TicketEnumCompatibilityTest {

    @Test
    void ticketBucketSupportsLegacyAliases() {
        assertEquals(TicketBucket.REPORT, TicketBucket.fromCanonicalId("player"));
        assertEquals(TicketBucket.REPORT, TicketBucket.fromCanonicalId("chat_report"));
        assertEquals(TicketBucket.STAFF, TicketBucket.fromCanonicalId("application"));
        assertEquals(TicketBucket.STAFF, TicketBucket.fromCanonicalId("staff application"));
    }

    @Test
    void ticketCategorySupportsLegacyAliases() {
        assertEquals(TicketCategory.APPLICATION, TicketCategory.fromCanonicalId("staff"));
        assertEquals(TicketCategory.APPEAL, TicketCategory.fromCanonicalId("ban_appeal"));
        assertEquals(TicketCategory.SUPPORT, TicketCategory.fromCanonicalId("general_support"));
        assertEquals(TicketCategory.BUG, TicketCategory.fromCanonicalId("bug-report"));
    }

    @Test
    void ticketStatusSupportsLegacyAliases() {
        assertEquals(TicketStatus.UNFINISHED, TicketStatus.fromCanonicalId("draft"));
        assertEquals(TicketStatus.OPEN, TicketStatus.fromCanonicalId("in_progress"));
        assertEquals(TicketStatus.CLOSED, TicketStatus.fromCanonicalId("resolved"));
        assertEquals(TicketStatus.OPEN, TicketStatus.fromCanonicalId("in progress"));
    }

    @Test
    void ticketPrioritySupportsLegacyAliases() {
        assertEquals(TicketPriority.NORMAL, TicketPriority.fromCanonicalId("medium"));
        assertEquals(TicketPriority.HIGH, TicketPriority.fromCanonicalId("urgent"));
        assertEquals(TicketPriority.LOW, TicketPriority.fromCanonicalId("minor"));
    }

    @Test
    void appealWorkflowSupportsLegacyAliases() {
        assertEquals(AppealWorkflowStatus.UNDER_REVIEW, AppealWorkflowStatus.fromCanonicalId("under review"));
        assertEquals(AppealWorkflowStatus.APPROVED, AppealWorkflowStatus.fromCanonicalId("accepted"));
        assertEquals(AppealWorkflowStatus.REJECTED, AppealWorkflowStatus.fromCanonicalId("denied"));
        assertEquals(AppealWorkflowStatus.REJECTED, AppealWorkflowStatus.fromCanonicalId("dismissed"));
    }
}
