package gg.modl.backend.ticket.service;

import gg.modl.backend.database.mongo.repository.TicketVerificationMongoRepository;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.email.EmailHTMLTemplate;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.infrastructure.onetimecode.OneTimeCodeCodec;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketVerification;
import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import java.util.Date;
import java.util.UUID;
import gg.modl.backend.ticket.config.TicketEmailVerificationConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketEmailVerificationService {
    private final TicketVerificationMongoRepository ticketVerificationRepository;
    private final EmailService emailService;
    private final TicketEmailVerificationConfiguration verificationConfig;
    private final OneTimeCodeCodec oneTimeCodeCodec;
    private static final int CODE_LENGTH = 6;
    private static final String CREATOR_EMAIL_KEY = "creatorEmail";
    private static final String CONTACT_EMAIL_KEY = "contactEmail";

    public String sendVerificationCode(Server server, Ticket ticket) {
        String email = getCreatorEmail(ticket);
        if (email == null || email.isBlank()) {
            throw new ValidationException("No valid email associated with this ticket");
        }

        String code = oneTimeCodeCodec.generateNumericCode(CODE_LENGTH);
        String codeHash = hash(code);

        TicketVerification verification = TicketVerification.builder()
            .id(UUID.randomUUID().toString())
            .ticketId(ticket.getId())
            .codeHash(codeHash)
            .email(email)
            .expiresAt(new Date(System.currentTimeMillis() + (verificationConfig.getCodeExpirySeconds() * 1000L)))
            .build();
        ticketVerificationRepository.replaceCodeVerification(server, verification);

        try {
            String serverName = server.getServerName() != null ? server.getServerName() : "Server";
            EmailHTMLTemplate.HTMLEmail emailContent = EmailHTMLTemplate.TICKET_VERIFICATION_CODE.build(serverName, code);
            emailService.send(email, emailContent);
        } catch (Exception e) {
            log.error("Failed to send verification code email for ticket {}", ticket.getId(), e);
            throw new ExternalServiceException("Failed to send verification email", e);
        }

        return EmailAddressUtil.mask(email);
    }

    private String getCreatorEmail(Ticket ticket) {
        String email = resolveContactEmail(ticket);
        if (email == null) {
            return null;
        }

        String normalizedEmail = EmailAddressUtil.normalizeIfValid(email);
        if (normalizedEmail == null) {
            log.warn("Skipping ticket verification email for {} due to invalid contact email: {}", ticket.getId(), email);
        }

        return normalizedEmail;
    }

    public static String resolveContactEmail(Ticket ticket) {
        if (ticket.getData() == null) {
            return null;
        }
        Object email = ticket.getData().get(CREATOR_EMAIL_KEY);
        if (email == null) {
            email = ticket.getData().get(CONTACT_EMAIL_KEY);
        }
        if (email == null) {
            return null;
        }
        String value = email.toString();
        return value.isBlank() ? null : value;
    }

    private String hash(String code) {
        return oneTimeCodeCodec.hash(code, verificationConfig.getCodeHashSecret());
    }

    public String verifyCode(Server server, String ticketId, String code) {
        String codeHash = hash(code);
        Date now = new Date();
        TicketVerification verification = ticketVerificationRepository.consumeMatchingCode(server, ticketId, codeHash, now)
            .orElse(null);
        if (verification == null) {
            ticketVerificationRepository.incrementFailedAttempts(server, ticketId, now);
            return null;
        }

        String token = UUID.randomUUID().toString();
        TicketVerification tokenVerification = TicketVerification.builder()
            .id(UUID.randomUUID().toString())
            .ticketId(ticketId)
            .token(token)
            .email(verification.getEmail())
            .expiresAt(new Date(System.currentTimeMillis() + (verificationConfig.getTokenExpirySeconds() * 1000L)))
            .build();
        ticketVerificationRepository.saveEntity(server, tokenVerification);
        return token;
    }

    public boolean validateToken(Server server, String ticketId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return ticketVerificationRepository.existsActiveToken(server, ticketId, token, new Date());
    }

    public boolean validateAppealCreateToken(Server server, String punishmentId, String playerUuid, String token) {
        return validateToken(server, appealCreateSubject(punishmentId, playerUuid), token);
    }

    private static String appealCreateSubject(String punishmentId, String playerUuid) {
        return "appeal-create:" + punishmentId + ":" + (playerUuid == null ? "" : playerUuid);
    }
}
