package gg.modl.backend.ticket.service;

import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.AppealWorkflowStatus;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketStatus;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppealWorkflowTransitionService {
    private final TicketMongoRepository ticketRepository;
    private final TicketNotificationService notificationService;

    public void applyOutcomeForPunishment(Server server, String appealId, String punishmentId,
                                          AppealWorkflowStatus outcome, String issuerName) {
        Ticket appeal = ticketRepository.findById(server, appealId)
            .filter(t -> t.getType() == TicketCategory.APPEAL)
            .filter(t -> isLinkedToPunishment(t, punishmentId))
            .orElse(null);
        if (appeal == null || appeal.getAppealWorkflowStatus() == outcome) {
            return;
        }
        requireReopenBeforeChangingTerminalStatus(appeal.getAppealWorkflowStatus(), outcome);

        boolean wasClosed = appeal.getStatus() != null && appeal.getStatus().isTerminal();
        TicketStatus lifecycleStatus = outcome.toTicketStatus();
        ticketRepository.updateAppealState(server, appealId, outcome, lifecycleStatus,
            lifecycleStatus.isTerminal(), null, List.of(statusChangeReply(issuerName, outcome)));

        if (!wasClosed && lifecycleStatus.isTerminal()) {
            appeal.setAppealWorkflowStatus(outcome);
            appeal.applyLifecycleStatus(lifecycleStatus);
            notificationService.notifyTicketClosed(server, appeal);
        }
    }

    public void requireReopenBeforeChangingTerminalStatus(AppealWorkflowStatus current, AppealWorkflowStatus requested) {
        if (current != null && requested != null
            && current.isTerminal() && requested.isTerminal() && requested != current) {
            throw new ConflictException("Appeal is already " + current.getDisplayName()
                + " and cannot be changed directly to " + requested.getDisplayName()
                + "; reopen the appeal first.");
        }
    }

    public TicketReply statusChangeReply(String staffUsername, AppealWorkflowStatus outcome) {
        return TicketReply.builder()
            .id(UUID.randomUUID().toString())
            .name(staffUsername != null ? staffUsername : "System")
            .content("Appeal status changed to " + outcome.getDisplayName() + ".")
            .type("system")
            .created(new Date())
            .staff(true)
            .action("APPEAL_STATUS_" + outcome.name())
            .build();
    }

    private static boolean isLinkedToPunishment(Ticket appeal, String punishmentId) {
        Map<String, Object> data = appeal.getData();
        return data != null && punishmentId != null && punishmentId.equals(data.get("punishmentId"));
    }
}
