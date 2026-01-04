package gg.modl.backend.appeal.service;

import gg.modl.backend.appeal.dto.request.AddAppealReplyRequest;
import gg.modl.backend.appeal.dto.request.CreateAppealRequest;
import gg.modl.backend.appeal.dto.request.UpdateAppealStatusRequest;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppealService {
    private final DynamicMongoTemplateProvider mongoProvider;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String APPEAL_TYPE = "appeal";
    private static final Set<String> VALID_STATUSES = Set.of(
            "Open", "Closed", "Under Review", "Pending Player Response",
            "Resolved", "Approved", "Denied", "Accepted", "Rejected"
    );

    public List<TicketResponse> getAppealsByPunishment(Server server, String punishmentId) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(
                Criteria.where("type").is(APPEAL_TYPE)
                        .and("data.punishmentId").is(punishmentId)
        );
        List<Ticket> appeals = template.find(query, Ticket.class, CollectionName.TICKETS);
        return appeals.stream().map(this::toTicketResponse).toList();
    }

    public Optional<TicketResponse> getAppealById(Server server, String appealId) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("_id").is(appealId));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);

        if (ticket == null || !APPEAL_TYPE.equals(ticket.getType())) {
            return Optional.empty();
        }

        return Optional.of(toTicketResponse(ticket));
    }

    public TicketResponse createAppeal(Server server, CreateAppealRequest request) {
        MongoTemplate template = getTemplate(server);

        Player player = findPlayerWithPunishment(template, request.playerUuid(), request.punishmentId());
        if (player == null) {
            throw new IllegalArgumentException("Punishment not found for the specified player");
        }

        Punishment punishment = player.getPunishments().stream()
                .filter(p -> p.getId().equals(request.punishmentId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Punishment details not found"));

        Query existingQuery = Query.query(
                Criteria.where("type").is(APPEAL_TYPE)
                        .and("data.punishmentId").is(request.punishmentId())
        );
        if (template.exists(existingQuery, Ticket.class, CollectionName.TICKETS)) {
            throw new IllegalStateException("An appeal already exists for this punishment");
        }

        String appealId = generateAppealId(template);

        Map<String, Object> data = new HashMap<>();
        data.put("punishmentId", request.punishmentId());
        data.put("playerUuid", request.playerUuid());
        data.put("contactEmail", request.email());

        if (request.additionalData() != null) {
            data.putAll(request.additionalData());
        }

        int typeOrdinal = punishment.getType_ordinal();
        String typeTag = switch (typeOrdinal) {
            case 1 -> "mute";
            case 2 -> "ban";
            default -> "punishment";
        };

        String username = getLatestUsername(player);

        String initialContent = buildInitialContent(request);

        TicketReply initialReply = TicketReply.builder()
                .id(UUID.randomUUID().toString())
                .name(username)
                .content(initialContent)
                .type("player")
                .created(new Date())
                .staff(false)
                .attachments(request.attachments() != null ? request.attachments() : new ArrayList<>())
                .build();

        Ticket appeal = Ticket.builder()
                .id(appealId)
                .type(APPEAL_TYPE)
                .category(APPEAL_TYPE)
                .status("Open")
                .subject("Appeal for Punishment: " + request.punishmentId())
                .tags(new ArrayList<>(List.of("appeal", typeTag)))
                .creator(username)
                .creatorUuid(request.playerUuid())
                .notes(new ArrayList<>())
                .replies(new ArrayList<>(List.of(initialReply)))
                .data(data)
                .locked(false)
                .created(new Date())
                .updatedAt(new Date())
                .build();

        template.save(appeal, CollectionName.TICKETS);

        linkAppealToPunishment(template, request.playerUuid(), request.punishmentId(), appealId);

        return toTicketResponse(appeal);
    }

    public Optional<TicketReply> addReply(Server server, String appealId, AddAppealReplyRequest request) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(Criteria.where("_id").is(appealId));
        Ticket appeal = template.findOne(query, Ticket.class, CollectionName.TICKETS);

        if (appeal == null || !APPEAL_TYPE.equals(appeal.getType())) {
            return Optional.empty();
        }

        if (appeal.isLocked()) {
            throw new IllegalStateException("Appeal is locked and cannot accept new replies");
        }

        TicketReply newReply = TicketReply.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .content(request.content())
                .type(request.type())
                .created(new Date())
                .staff(request.staff())
                .action(request.action())
                .avatar(request.avatar())
                .attachments(request.attachments() != null ? request.attachments() : new ArrayList<>())
                .build();

        Update update = new Update()
                .push("replies", newReply)
                .set("updatedAt", new Date());

        template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);

        return Optional.of(newReply);
    }

    public Optional<TicketResponse> updateStatus(Server server, String appealId, UpdateAppealStatusRequest request) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(Criteria.where("_id").is(appealId));
        Ticket appeal = template.findOne(query, Ticket.class, CollectionName.TICKETS);

        if (appeal == null || !APPEAL_TYPE.equals(appeal.getType())) {
            return Optional.empty();
        }

        Update update = new Update().set("updatedAt", new Date());
        List<TicketReply> systemReplies = new ArrayList<>();
        boolean statusChanged = false;

        if (request.status() != null && !request.status().equals(appeal.getStatus())) {
            if (!VALID_STATUSES.contains(request.status())) {
                throw new IllegalArgumentException("Invalid status value: " + request.status());
            }
            update.set("status", request.status());
            appeal.setStatus(request.status());
            statusChanged = true;

            systemReplies.add(createSystemReply(
                    request.staffUsername(),
                    "Status changed to " + request.status() + ".",
                    "STATUS_" + request.status().toUpperCase().replace(" ", "_")
            ));
        }

        if (request.resolution() != null) {
            Map<String, Object> data = appeal.getData() != null ? new HashMap<>(appeal.getData()) : new HashMap<>();
            if (!request.resolution().equals(data.get("resolution"))) {
                data.put("resolution", request.resolution());
                update.set("data", data);
                appeal.setData(data);

                systemReplies.add(createSystemReply(
                        request.staffUsername(),
                        "Resolution set to " + request.resolution() + ".",
                        "RESOLUTION_" + request.resolution().toUpperCase().replace(" ", "_")
                ));
            }
        }

        if (request.locked() != null && request.locked() != appeal.isLocked()) {
            update.set("locked", request.locked());
            appeal.setLocked(request.locked());

            systemReplies.add(createSystemReply(
                    request.staffUsername(),
                    "Ticket " + (request.locked() ? "locked" : "unlocked") + ".",
                    request.locked() ? "LOCKED" : "UNLOCKED"
            ));
        }

        for (TicketReply reply : systemReplies) {
            update.push("replies", reply);
            appeal.getReplies().add(reply);
        }

        template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);

        if (shouldPardonPunishment(request.status(), request.resolution())) {
            pardonPunishment(template, appeal, request.staffUsername());
        }

        return Optional.of(toTicketResponse(appeal));
    }

    private boolean shouldPardonPunishment(String status, String resolution) {
        boolean isClosed = "Closed".equals(status) || "Resolved".equals(status);
        boolean isApproved = "Approved".equals(resolution) || "Accepted".equals(resolution);
        return isClosed && isApproved;
    }

    private void pardonPunishment(MongoTemplate template, Ticket appeal, String staffUsername) {
        Map<String, Object> data = appeal.getData();
        if (data == null) return;

        String punishmentId = (String) data.get("punishmentId");
        String playerUuid = (String) data.get("playerUuid");

        if (punishmentId == null || playerUuid == null) return;

        Query playerQuery = Query.query(
                Criteria.where("minecraftUuid").is(playerUuid)
                        .and("punishments.id").is(punishmentId)
        );

        PunishmentModification modification = new PunishmentModification(
                "APPEAL_ACCEPT",
                new Date(),
                staffUsername != null ? staffUsername : "System",
                "Appeal approved",
                null,
                appeal.getId()
        );

        Update update = new Update()
                .push("punishments.$.modifications", modification)
                .set("punishments.$.data.active", false)
                .set("punishments.$.data.appealOutcome", "Approved")
                .set("punishments.$.data.appealTicketId", appeal.getId());

        template.updateFirst(playerQuery, update, Player.class, CollectionName.PLAYERS);
    }

    private TicketReply createSystemReply(String staffUsername, String content, String action) {
        return TicketReply.builder()
                .id(UUID.randomUUID().toString())
                .name(staffUsername != null ? staffUsername : "System")
                .content(content)
                .type("system")
                .created(new Date())
                .staff(true)
                .action(action)
                .build();
    }

    private String generateAppealId(MongoTemplate template) {
        String appealId;
        int attempts = 0;

        do {
            int randomId = 100000 + RANDOM.nextInt(900000);
            appealId = "APPEAL-" + randomId;
            attempts++;
        } while (template.exists(Query.query(Criteria.where("_id").is(appealId)), Ticket.class, CollectionName.TICKETS) && attempts < 10);

        return appealId;
    }

    private Player findPlayerWithPunishment(MongoTemplate template, String playerUuid, String punishmentId) {
        Query query = Query.query(
                Criteria.where("minecraftUuid").is(playerUuid)
                        .and("punishments.id").is(punishmentId)
        );
        return template.findOne(query, Player.class, CollectionName.PLAYERS);
    }

    private void linkAppealToPunishment(MongoTemplate template, String playerUuid, String punishmentId, String appealId) {
        Query query = Query.query(
                Criteria.where("minecraftUuid").is(playerUuid)
                        .and("punishments.id").is(punishmentId)
        );
        Update update = new Update().push("punishments.$.attachedTicketIds", appealId);
        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);
    }

    private String getLatestUsername(Player player) {
        if (player.getUsernames() == null || player.getUsernames().isEmpty()) {
            return player.getMinecraftUuid().toString();
        }
        return player.getUsernames().get(player.getUsernames().size() - 1).username();
    }

    private String buildInitialContent(CreateAppealRequest request) {
        StringBuilder content = new StringBuilder();

        if (request.reason() != null && !request.reason().isBlank()) {
            content.append("Appeal Reason: ").append(request.reason()).append("\n");
        }

        if (request.evidence() != null && !request.evidence().isBlank()) {
            content.append("Evidence: ").append(request.evidence()).append("\n");
        }

        if (request.additionalData() != null && !request.additionalData().isEmpty()) {
            content.append("\nAdditional Information:\n");
            for (Map.Entry<String, Object> entry : request.additionalData().entrySet()) {
                Object value = entry.getValue();
                if (value != null) {
                    String fieldLabel = request.fieldLabels() != null && request.fieldLabels().containsKey(entry.getKey())
                            ? request.fieldLabels().get(entry.getKey())
                            : formatFieldLabel(entry.getKey());

                    if (value instanceof List<?> list) {
                        if (!list.isEmpty()) {
                            content.append(fieldLabel).append(":\n");
                            for (Object item : list) {
                                content.append("  - ").append(item).append("\n");
                            }
                        }
                    } else if (value instanceof Boolean bool) {
                        content.append(fieldLabel).append(": ").append(bool ? "Yes" : "No").append("\n");
                    } else {
                        content.append(fieldLabel).append(": ").append(value).append("\n");
                    }
                }
            }
        }

        content.append("\nContact Email: ").append(request.email());

        if (content.toString().trim().equals("Contact Email: " + request.email())) {
            return "Appeal submitted for punishment " + request.punishmentId() + ".\n\nContact Email: " + request.email();
        }

        return content.toString();
    }

    private String formatFieldLabel(String key) {
        return key.replaceAll("([A-Z])", " $1")
                .replaceFirst("^.", String.valueOf(Character.toUpperCase(key.charAt(0))));
    }

    private TicketResponse toTicketResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getType(),
                "Ban Appeal",
                ticket.getSubject() != null ? ticket.getSubject() : "No Subject",
                ticket.getStatus(),
                ticket.getCreator(),
                ticket.getCreatorUuid(),
                ticket.getCreator(),
                ticket.getReportedPlayer(),
                ticket.getReportedPlayerUuid(),
                ticket.getCreated(),
                ticket.isLocked(),
                ticket.getReplies(),
                ticket.getNotes(),
                ticket.getTags(),
                ticket.getFormData(),
                ticket.getData(),
                ticket.getChatMessages()
        );
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }
}
