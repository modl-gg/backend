package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.PunishmentMongoRepository;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.infrastructure.validation.SafeUrls;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import gg.modl.backend.infrastructure.util.IdGenerator;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PunishmentEvidenceService {
    private final PlayerMongoRepository playerRepository;
    private final PunishmentMongoRepository punishmentRepository;
    private final PunishmentQueryService punishmentQueryService;
    private final PunishmentRealtimePublisher realtimePublisher;

    public PunishmentOperationResult addEvidence(Server server, String punishmentId, String evidenceUrl, String issuerName, String issuerId) {
        SafeUrls.requireSafe(evidenceUrl, "Invalid evidence URL");
        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        String resolvedIssuerName = issuerId != null ? null : issuerName;
        Date now = new Date();
        PunishmentEvidence evidence = new PunishmentEvidence(
            null,
            evidenceUrl,
            "url",
            resolvedIssuerName,
            issuerId,
            now,
            null,
            null,
            null
        );
        PunishmentNote note = new PunishmentNote(
            IdGenerator.generateShortId(),
            "added evidence",
            now,
            resolvedIssuerName,
            issuerId
        );
        context.punishment().getEvidence().add(evidence);
        context.punishment().getNotes().add(note);
        punishmentRepository.appendEvidence(server, context.player().getMinecraftUuid().toString(), punishmentId,
            List.of(evidence), note);

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Evidence added", true, 1);
    }

    public Player addEvidence(Server server, UUID playerUuid, String punishmentId, AddEvidenceRequest request) {
        SafeUrls.requireSafe(request.url(), "Invalid evidence URL");
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString())
            .orElseThrow(() -> new ResourceNotFoundException("Player not found"));

        Punishment punishment = findPunishment(player, punishmentId);
        if (punishment == null) {
            throw new ResourceNotFoundException("Punishment not found");
        }

        String evIssuerId = request.issuerId();
        String evIssuerName = evIssuerId != null ? null : (request.issuerName() != null ? request.issuerName() : "System");

        PunishmentEvidence evidence = new PunishmentEvidence(
            request.text(),
            request.url(),
            request.type(),
            evIssuerName,
            evIssuerId,
            new Date(),
            request.fileName(),
            request.fileType(),
            request.fileSize()
        );
        punishment.getEvidence().add(evidence);
        punishmentRepository.appendEvidence(server, player.getMinecraftUuid().toString(), punishmentId, List.of(evidence), null);
        realtimePublisher.punishmentDetailsChanged(server, player, punishment);
        return player;
    }

    private Punishment findPunishment(Player player, String punishmentId) {
        return PunishmentQueryService.findPunishment(player, punishmentId);
    }

    public PunishmentOperationResult addUploadedEvidence(Server server, String punishmentId, String issuerName, String issuerId, List<UploadedEvidenceItem> evidenceItems) {
        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        String resolvedIssuerName = issuerId != null ? null : issuerName;
        Date now = new Date();
        List<PunishmentEvidence> evidenceList = new ArrayList<>();
        for (UploadedEvidenceItem evidenceItem : evidenceItems) {
            PunishmentEvidence evidence = new PunishmentEvidence(
                null,
                evidenceItem.url(),
                "file",
                resolvedIssuerName,
                issuerId,
                now,
                evidenceItem.fileName(),
                evidenceItem.fileType(),
                evidenceItem.fileSize()
            );
            evidenceList.add(evidence);
            context.punishment().getEvidence().add(evidence);
        }
        PunishmentNote note = new PunishmentNote(
            IdGenerator.generateShortId(),
            "uploaded " + evidenceItems.size() + " evidence file(s)",
            now,
            resolvedIssuerName,
            issuerId
        );
        context.punishment().getNotes().add(note);
        punishmentRepository.appendEvidence(server, context.player().getMinecraftUuid().toString(), punishmentId, evidenceList, note);

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Evidence uploaded successfully", true, evidenceItems.size());
    }

    public PunishmentOperationResult addPunishmentNote(Server server, String punishmentId, String text, String issuerName, String issuerId) {
        PunishmentContext context = punishmentQueryService.findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0);
        }

        String resolvedIssuerName = issuerId != null ? null : issuerName;
        PunishmentNote note = new PunishmentNote(IdGenerator.generateShortId(), text, new Date(), resolvedIssuerName, issuerId);
        context.punishment().getNotes().add(note);
        punishmentRepository.addPunishmentNote(server, context.player().getMinecraftUuid().toString(), punishmentId, note, null);

        return new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "Note added", true, 1);
    }

    public Player addPunishmentNote(Server server, UUID playerUuid, String punishmentId, String text, String issuerName, String issuerId) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString())
            .orElseThrow(() -> new ResourceNotFoundException("Player not found"));

        Punishment punishment = findPunishment(player, punishmentId);
        if (punishment == null) {
            throw new ResourceNotFoundException("Punishment not found");
        }

        String resolvedIssuerName = issuerId != null ? null : issuerName;
        PunishmentNote note = new PunishmentNote(IdGenerator.generateShortId(), text, new Date(), resolvedIssuerName, issuerId);
        punishment.getNotes().add(note);
        punishmentRepository.addPunishmentNote(server, player.getMinecraftUuid().toString(), punishmentId, note, null);
        realtimePublisher.punishmentDetailsChanged(server, player, punishment);
        return player;
    }
}
