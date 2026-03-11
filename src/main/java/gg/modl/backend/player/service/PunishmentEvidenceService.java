package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentEvidence;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.player.dto.request.AddEvidenceRequest;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentContext;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationResult;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationStatus;
import gg.modl.backend.player.service.PunishmentQueryService.UploadedEvidenceItem;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PunishmentEvidenceService {
    private final PlayerMongoRepository playerRepository;
    private final PunishmentQueryService punishmentQueryService;

    public PunishmentOperationResult addEvidence(Server server, String punishmentId, String evidenceUrl, String issuerName, String issuerId) {
        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        String resolvedIssuerName = issuerId != null ? null : issuerName;
        Date now = new Date();
        ensurePunishmentCollections(context.punishment());
        context.punishment().getEvidence().add(new PunishmentEvidence(
                null,
                evidenceUrl,
                "url",
                resolvedIssuerName,
                issuerId,
                now,
                null,
                null,
                null
        ));
        context.punishment().getNotes().add(new PunishmentNote(
                new ObjectId().toHexString(),
                "added evidence",
                now,
                resolvedIssuerName,
                issuerId
        ));
        persistPlayerPunishments(server, context.player());

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Evidence added", true, 1);
    }

    public Player addEvidence(Server server, UUID playerUuid, String punishmentId, AddEvidenceRequest request) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString()).orElse(null);
        if (player == null) {
            return null;
        }

        Punishment punishment = findPunishment(player, punishmentId);
        if (punishment == null) {
            return null;
        }

        String evIssuerId = request.issuerId();
        String evIssuerName = evIssuerId != null ? null : (request.issuerName() != null ? request.issuerName() : "System");

        ensurePunishmentCollections(punishment);
        punishment.getEvidence().add(new PunishmentEvidence(
                request.text(),
                request.url(),
                request.type(),
                evIssuerName,
                evIssuerId,
                new Date(),
                request.fileName(),
                request.fileType(),
                request.fileSize()
        ));

        persistPlayerPunishments(server, player);
        return player;
    }

    public PunishmentOperationResult addUploadedEvidence(Server server, String punishmentId, String issuerName, String issuerId, List<UploadedEvidenceItem> evidenceItems) {
        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        String resolvedIssuerName = issuerId != null ? null : issuerName;
        Date now = new Date();
        ensurePunishmentCollections(context.punishment());
        for (UploadedEvidenceItem evidenceItem : evidenceItems) {
            context.punishment().getEvidence().add(new PunishmentEvidence(
                    null,
                    evidenceItem.url(),
                    "file",
                    resolvedIssuerName,
                    issuerId,
                    now,
                    evidenceItem.fileName(),
                    evidenceItem.fileType(),
                    evidenceItem.fileSize()
            ));
        }
        context.punishment().getNotes().add(new PunishmentNote(
                new ObjectId().toHexString(),
                "uploaded " + evidenceItems.size() + " evidence file(s)",
                now,
                resolvedIssuerName,
                issuerId
        ));
        persistPlayerPunishments(server, context.player());

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Evidence uploaded successfully", true, evidenceItems.size());
    }

    public PunishmentOperationResult addPunishmentNote(Server server, String punishmentId, String text, String issuerName, String issuerId) {
        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        String resolvedIssuerName = issuerId != null ? null : issuerName;
        ensurePunishmentCollections(context.punishment());
        context.punishment().getNotes().add(new PunishmentNote(new ObjectId().toHexString(), text, new Date(), resolvedIssuerName, issuerId));
        persistPlayerPunishments(server, context.player());

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Note added", true, 1);
    }

    public Player addPunishmentNote(Server server, UUID playerUuid, String punishmentId, String text, String issuerName, String issuerId) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString()).orElse(null);
        if (player == null) {
            return null;
        }

        Punishment punishment = findPunishment(player, punishmentId);
        if (punishment == null) {
            return null;
        }

        String resolvedIssuerName = issuerId != null ? null : issuerName;
        ensurePunishmentCollections(punishment);
        punishment.getNotes().add(new PunishmentNote(new ObjectId().toHexString(), text, new Date(), resolvedIssuerName, issuerId));
        persistPlayerPunishments(server, player);
        return player;
    }

    private Punishment findPunishment(Player player, String punishmentId) {
        if (player.getPunishments() == null || player.getPunishments().isEmpty()) {
            return null;
        }
        return player.getPunishments().stream()
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
}
