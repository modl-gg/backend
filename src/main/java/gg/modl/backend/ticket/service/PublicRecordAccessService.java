package gg.modl.backend.ticket.service;

import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublicRecordAccessService {
    private final TicketEmailVerificationService verificationService;
    private final PublicAccessProperties properties;

    public enum Access {
        GRANTED,
        TOKEN_REQUIRED,
        NOT_FOUND
    }

    public record AccessResult(Access access, String emailHint, boolean tokenVerified) {
        static AccessResult granted(boolean tokenVerified) {
            return new AccessResult(Access.GRANTED, null, tokenVerified);
        }

        static AccessResult notFound() {
            return new AccessResult(Access.NOT_FOUND, null, false);
        }

        static AccessResult tokenRequired(String emailHint) {
            return new AccessResult(Access.TOKEN_REQUIRED, emailHint, false);
        }
    }

    public AccessResult authorize(Server server, @Nullable Ticket record, @Nullable String presentedToken) {
        if (record == null || record.isHidden()) {
            return AccessResult.notFound();
        }
        if (hasValidToken(server, record, presentedToken)) {
            return AccessResult.granted(true);
        }

        String contactEmail = TicketEmailVerificationService.resolveContactEmail(record);
        if (contactEmail != null) {
            return AccessResult.tokenRequired(EmailAddressUtil.mask(contactEmail));
        }
        if (record.isEmailAuthEnabled()) {
            return AccessResult.tokenRequired(null);
        }
        if (properties.getEnforcement() == PublicAccessProperties.Enforcement.STRICT) {
            return AccessResult.tokenRequired(null);
        }
        return AccessResult.granted(false);
    }

    public AccessResult authorizeSubmission(Server server, @Nullable Ticket record, @Nullable String presentedToken) {
        if (record == null || record.isHidden()) {
            return AccessResult.notFound();
        }
        if (hasValidToken(server, record, presentedToken)) {
            return AccessResult.granted(true);
        }

        String contactEmail = TicketEmailVerificationService.resolveContactEmail(record);
        if (contactEmail != null) {
            return AccessResult.tokenRequired(EmailAddressUtil.mask(contactEmail));
        }
        return AccessResult.granted(false);
    }

    private boolean hasValidToken(Server server, Ticket record, @Nullable String presentedToken) {
        return presentedToken != null && !presentedToken.isBlank()
            && verificationService.validateToken(server, record.getId(), presentedToken);
    }
}
