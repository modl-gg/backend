package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.PunishmentMongoRepository;
import gg.modl.backend.exception.ResourceNotFoundException;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.modl.backend.util.IdGenerator;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PunishmentMutationService {
    private final PlayerMongoRepository playerRepository;
    private final PunishmentMongoRepository punishmentRepository;
    private final TicketMongoRepository ticketRepository;
    private final IssuerNameResolver issuerNameResolver;
    private final StaffMongoRepository staffRepository;
    private final PunishmentQueryService punishmentQueryService;
    private final PunishmentLifecycleService punishmentLifecycleService;

    public Player addModification(Server server, UUID playerUuid, String punishmentId, AddModificationRequest request) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString())
            .orElseThrow(() -> new ResourceNotFoundException("Player not found"));

        Date now = new Date();
        String modIssuerName = request.issuerId() != null ? null : request.issuerName();
        String modIssuerId = request.issuerId();

        PunishmentModification modification = new PunishmentModification(
            IdGenerator.generateShortId(),
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
            throw new ResourceNotFoundException("Punishment not found");
        }

        punishment.getModifications().add(modification);
        if (request.effectiveDuration() != null) {
            if (punishment.getStarted() == null) {
                punishment.setStarted(now);
            }
        }

        punishmentRepository.replacePunishments(server, player);
        return player;
    }

    private Punishment findPunishment(Player player, String punishmentId) {
        if (player.getPunishments().isEmpty()) {
            return null;
        }
        return player.getPunishments()
            .stream()
            .filter(punishment -> punishmentId.equals(punishment.getId()))
            .findFirst()
            .orElse(null);
    }

    public PunishmentOperationResult changeDuration(Server server, String punishmentId, Long newDuration, String issuerName, String issuerId) {
        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        String resolvedIssuerName = issuerId != null ? null : issuerName;

        Punishment punishment = context.punishment();
        Date now = new Date();

        punishment.getModifications().add(new PunishmentModification(
            IdGenerator.generateShortId(),
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
            IdGenerator.generateShortId(),
            "changed duration to " + durationText,
            now,
            resolvedIssuerName,
            issuerId
        ));
        punishment.getData().put("duration", newDuration);
        if (punishment.getStarted() == null) {
            punishment.setStarted(now);
        }

        punishmentRepository.replacePunishments(server, context.player());

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
        String resolvedIssuerName = issuerId != null ? null : issuerName;
        punishment.getData().put(toggleOption.dataKey, enabled);
        punishment.getNotes().add(new PunishmentNote(
            IdGenerator.generateShortId(),
            (enabled ? "enabled " : "disabled ") + toggleOption.displayName,
            now,
            resolvedIssuerName,
            issuerId
        ));
        punishmentRepository.replacePunishments(server, context.player());

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Option toggled", true, 1);
    }

    public PunishmentOperationResult acknowledgeStatWipe(Server server, String punishmentId) {
        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        Map<String, Object> data = context.punishment().getData();
        if (!Boolean.TRUE.equals(data.get("wipeAfterExpiry"))) {
            return new PunishmentOperationResult(
                PunishmentOperationStatus.NO_OP,
                "Stat wipe no longer enabled for this punishment",
                false,
                0
            );
        }

        Map<String, Object> updatedData = context.punishment().getData();
        updatedData.put("statWipeCompleted", true);
        updatedData.put("statWipeCompletedAt", new Date());
        punishmentRepository.replacePunishments(server, context.player());

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Stat wipe acknowledged", true, 1);
    }

    public PunishmentOperationResult modifyPunishmentTickets(Server server, String punishmentId, ModifyPunishmentTicketsRequest request) {
        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Failed to modify punishment tickets", false, 0);
        }

        applyPunishmentTicketModifications(server, context.player(), context.punishment(), request);
        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Punishment tickets modified", true, 1);
    }

    public Player modifyPunishmentTickets(Server server, UUID playerUuid, String punishmentId, ModifyPunishmentTicketsRequest request) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString())
            .orElseThrow(() -> new ResourceNotFoundException("Player not found"));

        Punishment punishment = player.getPunishments()
            .stream()
            .filter(p -> p.getId().equals(punishmentId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Punishment not found"));

        applyPunishmentTicketModifications(server, player, punishment, request);
        return player;
    }

    private void applyPunishmentTicketModifications(Server server, Player player, Punishment punishment, ModifyPunishmentTicketsRequest request) {

        List<String> currentIds = new ArrayList<>(punishment.getAttachedTicketIds());

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
        punishmentRepository.replacePunishments(server, player);

        if (request.modifyAssociatedTickets()) {
            String ticketIssuerName = issuerNameResolver.resolve(request.issuerId(), request.issuerName(), server, staffRepository);
            if (request.addTicketIds() != null && !request.addTicketIds().isEmpty()) {
                closeAttachedTickets(server, request.addTicketIds(), ticketIssuerName);
            }
            if (request.removeTicketIds() != null && !request.removeTicketIds().isEmpty()) {
                reopenAttachedTickets(server, request.removeTicketIds(), ticketIssuerName);
            }
        }
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
                log.error("[TICKET_CLOSE] Failed to close ticket {}", ticketId, e);
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
                log.error("[TICKET_REOPEN] Failed to reopen ticket {}", ticketId, e);
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
