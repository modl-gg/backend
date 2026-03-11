package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.player.dto.request.AddModificationRequest;
import gg.modl.backend.player.dto.request.ModifyPunishmentTicketsRequest;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentContext;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationResult;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketStatus;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PunishmentMutationService {
    private final PlayerMongoRepository playerRepository;
    private final TicketMongoRepository ticketRepository;
    private final IssuerNameResolver issuerNameResolver;
    private final StaffMongoRepository staffRepository;
    private final PunishmentQueryService punishmentQueryService;
    private final PunishmentLifecycleService punishmentLifecycleService;

    public Player addModification(Server server, UUID playerUuid, String punishmentId, AddModificationRequest request) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString()).orElse(null);
        if (player == null) {
            return null;
        }

        Date now = new Date();
        String modIssuerName = request.issuerId() != null ? null : request.issuerName();
        String modIssuerId = request.issuerId();

        PunishmentModification modification = new PunishmentModification(
            new ObjectId().toHexString(),
            request.type(),
            now,
            modIssuerName,
            modIssuerId,
            request.reason() != null ? request.reason() : "",
            request.effectiveDuration(),
            request.appealTicketId(),
            null
        );

        Punishment punishment = findPunishment(player, punishmentId);
        if (punishment == null) {
            return null;
        }

        ensurePunishmentCollections(punishment);
        punishment.getModifications().add(modification);
        if (request.effectiveDuration() != null) {
            if (punishment.getStarted() == null) {
                punishment.setStarted(now);
            }
        }

        persistPlayerPunishments(server, player);
        return player;
    }

    private Punishment findPunishment(Player player, String punishmentId) {
        if (player.getPunishments() == null || player.getPunishments().isEmpty()) {
            return null;
        }
        return player.getPunishments()
            .stream()
            .filter(punishment -> punishmentId.equals(punishment.getId()))
            .findFirst()
            .orElse(null);
    }

    private void ensurePunishmentCollections(Punishment punishment) {
        if (punishment.getModifications() == null) {
            punishment.setModifications(new ArrayList<>());
        }
        if (punishment.getNotes() == null) {
            punishment.setNotes(new ArrayList<>());
        }
        if (punishment.getEvidence() == null) {
            punishment.setEvidence(new ArrayList<>());
        }
        if (punishment.getAttachedTicketIds() == null) {
            punishment.setAttachedTicketIds(new ArrayList<>());
        }
    }

    private void persistPlayerPunishments(Server server, Player player) {
        if (player.getPunishments() == null) {
            player.setPunishments(new ArrayList<>());
        }
        playerRepository.replacePunishments(server, player);
    }

    public PunishmentOperationResult changeDuration(Server server, String punishmentId, Long newDuration, String issuerName, String issuerId) {
        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        String resolvedIssuerName = issuerId != null ? null : issuerName;

        Punishment punishment = context.punishment();
        Date now = new Date();
        ensurePunishmentCollections(punishment);

        punishment.getModifications().add(new PunishmentModification(
            new ObjectId().toHexString(),
            "MANUAL_DURATION_CHANGE",
            now,
            resolvedIssuerName,
            issuerId,
            "Duration changed",
            newDuration,
            null,
            null
        ));

        String durationText = newDuration == null || newDuration < 0
                              ? "permanent"
                              : PunishmentMapper.formatDuration(newDuration, false);
        punishment.getNotes().add(new PunishmentNote(
            new ObjectId().toHexString(),
            "changed duration to " + durationText,
            now,
            resolvedIssuerName,
            issuerId
        ));
        ensurePunishmentData(punishment).put("duration", newDuration);
        if (punishment.getStarted() == null) {
            punishment.setStarted(now);
        }

        persistPlayerPunishments(server, context.player());

        if (Boolean.TRUE.equals(punishment.getData().get("altBlocking"))) {
            int cascaded = punishmentLifecycleService.cascadeDurationChangeToLinkedBans(server, punishmentId, newDuration, issuerName);
            if (cascaded > 0) {
                return new PunishmentOperationResult(
                    PunishmentOperationStatus.SUCCESS,
                    "Duration changed (cascaded to " + cascaded + " linked ban" + (cascaded > 1 ? "s" : "") + ")",
                    true,
                    cascaded + 1
                );
            }
        }

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Duration changed", true, 1);
    }

    private Map<String, Object> ensurePunishmentData(Punishment punishment) {
        if (punishment.getData() == null) {
            punishment.setData(new HashMap<>());
        }
        return punishment.getData();
    }

    public PunishmentOperationResult toggleOption(Server server, String punishmentId, String option, boolean enabled, String issuerName, String issuerId) {
        PunishmentToggleOption toggleOption = PunishmentToggleOption.from(option);
        if (toggleOption == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.INVALID_REQUEST, "Invalid option", false, 0);
        }

        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        Punishment punishment = context.punishment();
        Date now = new Date();
        ensurePunishmentCollections(punishment);
        String resolvedIssuerName = issuerId != null ? null : issuerName;
        ensurePunishmentData(punishment).put(toggleOption.dataKey, enabled);
        punishment.getNotes().add(new PunishmentNote(
            new ObjectId().toHexString(),
            (enabled ? "enabled " : "disabled ") + toggleOption.displayName,
            now,
            resolvedIssuerName,
            issuerId
        ));
        persistPlayerPunishments(server, context.player());

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Option toggled", true, 1);
    }

    public PunishmentOperationResult acknowledgeStatWipe(Server server, String punishmentId) {
        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        Map<String, Object> data = context.punishment().getData();
        if (data == null || !Boolean.TRUE.equals(data.get("wipeAfterExpiry"))) {
            return new PunishmentOperationResult(
                PunishmentOperationStatus.NO_OP,
                "Stat wipe no longer enabled for this punishment",
                false,
                0
            );
        }

        Map<String, Object> updatedData = ensurePunishmentData(context.punishment());
        updatedData.put("statWipeCompleted", true);
        updatedData.put("statWipeCompletedAt", new Date());
        persistPlayerPunishments(server, context.player());

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Stat wipe acknowledged", true, 1);
    }

    public PunishmentOperationResult modifyPunishmentTickets(Server server, String punishmentId, ModifyPunishmentTicketsRequest request) {
        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Failed to modify punishment tickets", false, 0);
        }

        Player updated = modifyPunishmentTickets(server, context.player().getMinecraftUuid(), punishmentId, request);
        if (updated == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Failed to modify punishment tickets", false, 0);
        }

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Punishment tickets modified", true, 1);
    }

    public Player modifyPunishmentTickets(Server server, UUID playerUuid, String punishmentId, ModifyPunishmentTicketsRequest request) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString()).orElse(null);
        if (player == null) {
            return null;
        }

        Punishment punishment = player.getPunishments()
            .stream()
            .filter(p -> p.getId().equals(punishmentId))
            .findFirst()
            .orElse(null);
        if (punishment == null) {
            return null;
        }

        List<String> currentIds = punishment.getAttachedTicketIds() != null
                                  ? new ArrayList<>(punishment.getAttachedTicketIds())
                                  : new ArrayList<>();

        if (request.addTicketIds() != null) {
            for (String id : request.addTicketIds()) {
                if (!currentIds.contains(id)) {
                    currentIds.add(id);
                }
            }
        }

        if (request.removeTicketIds() != null) {
            currentIds.removeAll(request.removeTicketIds());
        }

        punishment.setAttachedTicketIds(currentIds);
        persistPlayerPunishments(server, player);

        if (request.modifyAssociatedTickets()) {
            String ticketIssuerName = issuerNameResolver.resolve(request.issuerId(), request.issuerName(), server, staffRepository);
            if (request.addTicketIds() != null && !request.addTicketIds().isEmpty()) {
                closeAttachedTickets(server, request.addTicketIds(), ticketIssuerName);
            }
            if (request.removeTicketIds() != null && !request.removeTicketIds().isEmpty()) {
                reopenAttachedTickets(server, request.removeTicketIds(), ticketIssuerName);
            }
        }

        return player;
    }

    private void closeAttachedTickets(Server server, List<String> ticketIds, String issuerName) {
        for (String ticketId : ticketIds) {
            try {
                Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
                if (ticket == null || ticket.isLocked()) {
                    continue;
                }

                if (ticket.getReplies() == null) {
                    ticket.setReplies(new ArrayList<>());
                }

                TicketReply systemReply = TicketReply.builder()
                    .id(UUID.randomUUID().toString())
                    .name(issuerName)
                    .content("Report accepted - punishment has been issued.")
                    .type("public")
                    .created(new Date())
                    .staff(true)
                    .action("report_accepted")
                    .attachments(new ArrayList<>())
                    .build();

                ticket.getReplies().add(systemReply);
                applyTicketLifecycleStatus(ticket, TicketStatus.CLOSED);
                ticket.setUpdatedAt(new Date());
                ticketRepository.updateState(server, ticket);
            } catch (Exception e) {
                log.error("[TICKET_CLOSE] Failed to close ticket {}: {}", ticketId, e.getMessage());
            }
        }
    }

    private void applyTicketLifecycleStatus(Ticket ticket, TicketStatus status) {
        ticket.setStatus(status);
        ticket.setLocked(status != null && status.isTerminal());
    }

    private void reopenAttachedTickets(Server server, List<String> ticketIds, String issuerName) {
        for (String ticketId : ticketIds) {
            try {
                Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
                if (ticket == null || !ticket.isLocked()) {
                    continue;
                }

                if (ticket.getReplies() == null) {
                    ticket.setReplies(new ArrayList<>());
                }

                TicketReply systemReply = TicketReply.builder()
                    .id(UUID.randomUUID().toString())
                    .name(issuerName)
                    .content("Ticket reopened - punishment association removed.")
                    .type("public")
                    .created(new Date())
                    .staff(true)
                    .action("report_reopened")
                    .attachments(new ArrayList<>())
                    .build();

                ticket.getReplies().add(systemReply);
                applyTicketLifecycleStatus(ticket, TicketStatus.OPEN);
                ticket.setUpdatedAt(new Date());
                ticketRepository.updateState(server, ticket);
            } catch (Exception e) {
                log.error("[TICKET_REOPEN] Failed to reopen ticket {}: {}", ticketId, e.getMessage());
            }
        }
    }

    private enum PunishmentToggleOption {
        ALT_BLOCKING("altBlocking", "alt-blocking"),
        STAT_WIPE("wipeAfterExpiry", "stat wipe");

        private final String dataKey;
        private final String displayName;

        PunishmentToggleOption(String dataKey, String displayName) {
            this.dataKey = dataKey;
            this.displayName = displayName;
        }

        private static PunishmentToggleOption from(String option) {
            if (option == null || option.isBlank()) {
                return null;
            }

            try {
                return PunishmentToggleOption.valueOf(option.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
