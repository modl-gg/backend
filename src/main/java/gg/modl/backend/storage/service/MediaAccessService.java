package gg.modl.backend.storage.service;

import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.service.TicketEmailVerificationService;
import gg.modl.backend.ticket.service.TicketService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaAccessService {
    private final TicketService ticketService;
    private final TicketEmailVerificationService verificationService;

    public AccessResult validatePublicUploadAccess(
        Server server,
        String uploadType,
        String entityId,
        String accessToken
    ) {
        if (entityId == null || entityId.isBlank()) {
            return AccessResult.denied("entityId is required for public uploads");
        }

        String normalizedEntityId = entityId.trim();
        String normalizedType = normalizeUploadType(uploadType);

        if ("new".equalsIgnoreCase(normalizedEntityId)) {
            if ("ticket".equals(normalizedType) || "appeal".equals(normalizedType)) {
                return AccessResult.allowed();
            }
            return AccessResult.denied("Temporary uploads are only allowed for ticket and appeal types");
        }

        Optional<Ticket> ticketOpt = ticketService.getTicketRaw(server, normalizedEntityId);
        if (ticketOpt.isEmpty() || ticketOpt.get().isHidden()) {
            return AccessResult.notFound();
        }

        Ticket ticket = ticketOpt.get();
        boolean isAppealTicket = ticket.getType() == TicketCategory.APPEAL;
        if ("appeal".equals(normalizedType) && !isAppealTicket) {
            return AccessResult.denied("Entity is not an appeal ticket");
        }
        if ("ticket".equals(normalizedType) && isAppealTicket) {
            return AccessResult.denied("Appeal uploads must use uploadType=appeal");
        }

        if (ticket.isEmailAuthEnabled()) {
            boolean validToken = accessToken != null
                                 && !accessToken.isBlank()
                                 && verificationService.validateToken(server, normalizedEntityId, accessToken);
            if (!validToken) {
                return AccessResult.denied("Email verification token required for this ticket");
            }
        }

        return AccessResult.allowed();
    }

    private String normalizeUploadType(String uploadType) {
        return "tickets".equals(uploadType) ? "ticket" : uploadType;
    }

    public enum AccessStatus {
        ALLOWED,
        DENIED,
        NOT_FOUND
    }

    public record AccessResult(AccessStatus status, String error) {
        static AccessResult allowed() {
            return new AccessResult(AccessStatus.ALLOWED, null);
        }

        static AccessResult denied(String error) {
            return new AccessResult(AccessStatus.DENIED, error);
        }

        static AccessResult notFound() {
            return new AccessResult(AccessStatus.NOT_FOUND, null);
        }

        public boolean isAllowed() {
            return status == AccessStatus.ALLOWED;
        }
    }
}
