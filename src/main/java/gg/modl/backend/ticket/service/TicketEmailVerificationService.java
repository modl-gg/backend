package gg.modl.backend.ticket.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.email.EmailHTMLTemplate;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketVerification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketEmailVerificationService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final EmailService emailService;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${modl.ticket.email-verification.code-expiry-seconds:300}")
    private long codeExpirySeconds;

    @Value("${modl.ticket.email-verification.token-expiry-seconds:300}")
    private long tokenExpirySeconds;

    /**
     * Send a verification code to the ticket creator's email.
     * Returns a masked email hint (e.g. "t***@example.com").
     */
    public String sendVerificationCode(Server server, Ticket ticket) {
        String email = getCreatorEmail(ticket);
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("No email associated with this ticket");
        }

        String code = String.format("%06d", RANDOM.nextInt(1000000));
        String codeHash = hashCode(code);

        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        // Remove any existing verification codes for this ticket
        template.remove(
                Query.query(Criteria.where("ticketId").is(ticket.getId()).and("token").exists(false)),
                TicketVerification.class,
                CollectionName.TICKET_VERIFICATIONS
        );

        // Store the hashed code
        TicketVerification verification = TicketVerification.builder()
                .id(UUID.randomUUID().toString())
                .ticketId(ticket.getId())
                .codeHash(codeHash)
                .email(email)
                .expiresAt(new Date(System.currentTimeMillis() + (codeExpirySeconds * 1000L)))
                .build();

        template.save(verification, CollectionName.TICKET_VERIFICATIONS);

        // Send the code via email
        try {
            String serverName = server.getServerName() != null ? server.getServerName() : "Server";
            EmailHTMLTemplate.HTMLEmail emailContent = EmailHTMLTemplate.TICKET_VERIFICATION_CODE.build(serverName, code);
            emailService.send(email, emailContent);
        } catch (Exception e) {
            log.error("Failed to send verification code email for ticket {}: {}", ticket.getId(), e.getMessage());
            throw new RuntimeException("Failed to send verification email");
        }

        return maskEmail(email);
    }

    /**
     * Verify a code and return a session token if valid.
     */
    public String verifyCode(Server server, String ticketId, String code) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        String codeHash = hashCode(code);

        Query query = Query.query(
                Criteria.where("ticketId").is(ticketId)
                        .and("codeHash").is(codeHash)
                        .and("token").exists(false)
                        .and("expiresAt").gte(new Date())
        );

        TicketVerification verification = template.findOne(query, TicketVerification.class, CollectionName.TICKET_VERIFICATIONS);

        if (verification == null) {
            return null;
        }

        // Generate a session token
        String token = UUID.randomUUID().toString();

        // Remove the code verification and create a token verification
        template.remove(query, TicketVerification.class, CollectionName.TICKET_VERIFICATIONS);

        TicketVerification tokenVerification = TicketVerification.builder()
                .id(UUID.randomUUID().toString())
                .ticketId(ticketId)
                .token(token)
                .email(verification.getEmail())
                .expiresAt(new Date(System.currentTimeMillis() + (tokenExpirySeconds * 1000L)))
                .build();

        template.save(tokenVerification, CollectionName.TICKET_VERIFICATIONS);

        return token;
    }

    /**
     * Validate a session token for a ticket.
     */
    public boolean validateToken(Server server, String ticketId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(
                Criteria.where("ticketId").is(ticketId)
                        .and("token").is(token)
                        .and("expiresAt").gte(new Date())
        );

        return template.exists(query, TicketVerification.class, CollectionName.TICKET_VERIFICATIONS);
    }

    private String getCreatorEmail(Ticket ticket) {
        if (ticket.getData() == null) return null;
        Object email = ticket.getData().get("creatorEmail");
        return email != null ? email.toString() : null;
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return email;
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash code", e);
        }
    }
}
