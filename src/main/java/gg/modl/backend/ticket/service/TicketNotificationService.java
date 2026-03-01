package gg.modl.backend.ticket.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.email.EmailHTMLTemplate;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for sending ticket notifications via email and in-game messages.
 *
 * When a staff member replies to a ticket:
 * 1. Sends an email to the ticket creator (if they provided an email)
 * 2. Creates an in-game notification for the player (if they have a Minecraft UUID)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketNotificationService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final EmailService emailService;

    /**
     * Send notifications when a staff member replies to a ticket.
     * This sends both email and in-game notifications asynchronously.
     */
    @Async
    public void notifyTicketReply(Server server, Ticket ticket, TicketReply reply) {
        if (!reply.isStaff()) {
            // Only send notifications for staff replies
            return;
        }

        String creatorEmail = getCreatorEmail(ticket);
        String creatorUuid = ticket.getCreatorUuid();

        // Send email notification if creator has an email
        if (creatorEmail != null && !creatorEmail.isBlank()) {
            sendEmailNotification(server, ticket, reply, creatorEmail);
        }

        // Create in-game notification if creator has a Minecraft UUID
        if (creatorUuid != null && !creatorUuid.isBlank()) {
            createInGameNotification(server, ticket, reply, creatorUuid);
        }
    }

    /**
     * Send email notification to the ticket creator.
     */
    private void sendEmailNotification(Server server, Ticket ticket, TicketReply reply, String toEmail) {
        try {
            String serverName = server.getServerName() != null ? server.getServerName() : "Server";
            String playerName = ticket.getCreatorName() != null ? ticket.getCreatorName() : "Player";
            String ticketType = ticket.getType() != null ? formatTicketType(ticket.getType()) : "Support";
            String ticketId = ticket.getId();
            String ticketSubject = ticket.getSubject() != null ? ticket.getSubject() : "No Subject";
            String replyAuthor = reply.getName() != null ? reply.getName() : "Staff";
            String replyContent = reply.getContent() != null ? reply.getContent() : "";
            String ticketUrl = buildTicketUrl(server, ticketId);

            EmailHTMLTemplate.HTMLEmail email = EmailHTMLTemplate.TICKET_REPLY_TEMPLATE.build(
                    serverName,
                    playerName,
                    true, // isStaffReply
                    ticketType,
                    ticketId,
                    ticketSubject,
                    replyAuthor,
                    replyContent,
                    ticketUrl
            );

            emailService.send(toEmail, email);
        } catch (Exception e) {
            log.error("Failed to send ticket reply email to {} for ticket {}: {}",
                    toEmail, ticket.getId(), e.getMessage());
        }
    }

    /**
     * Create in-game notification for the player.
     * The notification will be picked up by the Minecraft plugin during sync.
     */
    private void createInGameNotification(Server server, Ticket ticket, TicketReply reply, String playerUuid) {
        try {
            MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

            // Find the player by their Minecraft UUID
            Query query = Query.query(Criteria.where("minecraftUuid").is(playerUuid));
            Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

            if (player == null) {
                log.debug("Player with UUID {} not found, skipping in-game notification", playerUuid);
                return;
            }

            // Build the notification
            Map<String, Object> notification = new HashMap<>();
            notification.put("id", UUID.randomUUID().toString());
            notification.put("type", "TICKET_REPLY");
            notification.put("message", buildNotificationMessage(ticket, reply));
            notification.put("timestamp", System.currentTimeMillis());

            // Add data for clickable links in-game
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("ticketId", ticket.getId());
            notificationData.put("ticketUrl", buildTicketUrl(server, ticket.getId()));
            notificationData.put("replyAuthor", reply.getName());
            notification.put("data", notificationData);

            // Add to player's pending notifications
            Update update = new Update().push("data.pendingNotifications", notification);
            template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);
        } catch (Exception e) {
            log.error("Failed to create in-game notification for player {} for ticket {}: {}",
                    playerUuid, ticket.getId(), e.getMessage());
        }
    }

    /**
     * Send transcript email and in-game notification when a ticket is closed.
     */
    @Async
    public void notifyTicketClosed(Server server, Ticket ticket) {
        String creatorEmail = getCreatorEmail(ticket);
        String creatorUuid = ticket.getCreatorUuid();

        // Send transcript email if creator has an email
        if (creatorEmail != null && !creatorEmail.isBlank()) {
            sendTranscriptEmail(server, ticket, creatorEmail);
        }

        // Create in-game notification if creator has a Minecraft UUID
        if (creatorUuid != null && !creatorUuid.isBlank()) {
            createClosedInGameNotification(server, ticket, creatorUuid);
        }
    }

    /**
     * Send transcript email to the ticket creator.
     */
    private void sendTranscriptEmail(Server server, Ticket ticket, String toEmail) {
        try {
            String serverName = server.getServerName() != null ? server.getServerName() : "Server";
            String playerName = ticket.getCreatorName() != null ? ticket.getCreatorName() : "Player";
            String ticketType = ticket.getType() != null ? formatTicketType(ticket.getType()) : "Support";
            String ticketId = ticket.getId();
            String ticketSubject = ticket.getSubject() != null ? ticket.getSubject() : "No Subject";
            String ticketUrl = buildTicketUrl(server, ticketId);

            // Build messages HTML
            StringBuilder messagesHtml = new StringBuilder();
            if (ticket.getReplies() != null) {
                for (TicketReply reply : ticket.getReplies()) {
                    String author = reply.getName() != null ? reply.getName() : (reply.isStaff() ? "Staff" : "Player");
                    String date = reply.getCreated() != null ? reply.getCreated().toString() : "";
                    String content = reply.getContent() != null ? reply.getContent().replace("\n", "<br>") : "";
                    String roleLabel = reply.isStaff() ? " (Staff)" : "";

                    messagesHtml.append("""
                            <div style="border: 1px solid #e9ecef; border-radius: 4px; padding: 12px; margin: 10px 0;">
                              <div style="margin-bottom: 8px;">
                                <strong style="color: #333;">%s%s</strong>
                                <span style="color: #888; font-size: 12px; margin-left: 8px;">%s</span>
                              </div>
                              <div style="color: #555; font-size: 14px;">%s</div>
                            </div>
                            """.formatted(author, roleLabel, date, content));
                }
            }

            EmailHTMLTemplate.HTMLEmail email = EmailHTMLTemplate.TICKET_TRANSCRIPT_TEMPLATE.build(
                    serverName, playerName, ticketType, ticketId, ticketSubject, messagesHtml.toString(), ticketUrl
            );

            emailService.send(toEmail, email);
        } catch (Exception e) {
            log.error("Failed to send ticket transcript email to {} for ticket {}: {}",
                    toEmail, ticket.getId(), e.getMessage());
        }
    }

    /**
     * Create in-game notification when a ticket is closed.
     */
    private void createClosedInGameNotification(Server server, Ticket ticket, String playerUuid) {
        try {
            MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

            Query query = Query.query(Criteria.where("minecraftUuid").is(playerUuid));
            Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

            if (player == null) return;

            Map<String, Object> notification = new HashMap<>();
            notification.put("id", UUID.randomUUID().toString());
            notification.put("type", "TICKET_CLOSED");
            notification.put("message", String.format("Your ticket #%s has been closed", ticket.getId()));
            notification.put("timestamp", System.currentTimeMillis());

            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("ticketId", ticket.getId());
            notificationData.put("ticketUrl", buildTicketUrl(server, ticket.getId()));
            notification.put("data", notificationData);

            Update update = new Update().push("data.pendingNotifications", notification);
            template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);
        } catch (Exception e) {
            log.error("Failed to create in-game closed notification for player {} for ticket {}: {}",
                    playerUuid, ticket.getId(), e.getMessage());
        }
    }

    /**
     * Get the creator's email from ticket data.
     */
    private String getCreatorEmail(Ticket ticket) {
        if (ticket.getData() == null) {
            return null;
        }
        Object email = ticket.getData().get("creatorEmail");
        if (email == null) {
            return null;
        }

        String normalizedEmail = EmailAddressUtil.normalizeIfValid(email.toString());
        if (normalizedEmail == null) {
            log.warn("Skipping ticket email notification for {} due to invalid creator email: {}",
                    ticket.getId(),
                email);
            return null;
        }

        return normalizedEmail;
    }

    /**
     * Build the ticket URL for the public ticket view.
     */
    private String buildTicketUrl(Server server, String ticketId) {
        // Use custom domain override if set, otherwise use the subdomain
        String domain = server.getCustomDomainOverride();
        if (domain == null || domain.isBlank()) {
            domain = server.getServerName() + ".modl.gg";
        }
        return "https://" + domain + "/ticket/" + ticketId;
    }

    /**
     * Build the notification message for in-game display.
     */
    private String buildNotificationMessage(Ticket ticket, TicketReply reply) {
        String replyAuthor = reply.getName() != null ? reply.getName() : "Staff";
        return String.format("%s replied to your ticket #%s", replyAuthor, ticket.getId());
    }

    /**
     * Format ticket type for display.
     */
    private String formatTicketType(String type) {
        if (type == null) return "Support";
        return switch (type.toLowerCase()) {
            case "report", "player" -> "Report";
            case "appeal" -> "Appeal";
            case "support" -> "Support";
            case "bug" -> "Bug Report";
            case "application" -> "Application";
            default -> type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase();
        };
    }
}
