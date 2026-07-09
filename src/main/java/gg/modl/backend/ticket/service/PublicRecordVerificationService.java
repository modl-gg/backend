package gg.modl.backend.ticket.service;

import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublicRecordVerificationService {
    private final TicketEmailVerificationService verificationService;

    public String sendVerificationCode(Server server, Ticket record) {
        if (TicketEmailVerificationService.resolveContactEmail(record) == null) {
            throw new ValidationException("No email associated with this record");
        }
        return verificationService.sendVerificationCode(server, record);
    }

    public String verifyCode(Server server, String recordId, String code) {
        String token = verificationService.verifyCode(server, recordId, code);
        if (token == null) {
            throw new ForbiddenException("Invalid or expired code");
        }
        return token;
    }
}
