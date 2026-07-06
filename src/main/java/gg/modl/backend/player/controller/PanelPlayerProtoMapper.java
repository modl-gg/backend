package gg.modl.backend.player.controller;

import gg.modl.backend.infrastructure.proto.ProtoMapperSupport;
import gg.modl.backend.player.data.IPEntry;
import gg.modl.backend.player.data.NoteEntry;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.player.data.punishment.PunishmentEvidence;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.player.dto.response.LinkedAccountResponse;
import gg.modl.backend.player.dto.response.PlayerDetailResponse;
import gg.modl.backend.player.dto.response.PlayerSearchResult;
import gg.modl.backend.player.dto.request.AddEvidenceRequest;
import gg.modl.backend.player.dto.request.AddModificationRequest;
import gg.modl.backend.player.dto.request.CreateEvidenceRequest;
import gg.modl.backend.player.dto.request.CreateNoteRequest;
import gg.modl.backend.player.dto.request.CreatePunishmentRequest;
import gg.modl.backend.player.dto.request.ModifyPunishmentTicketsRequest;
import gg.modl.backend.player.dto.response.PunishmentResponse;
import gg.modl.backend.replay.dto.PlayerReplayResponse;
import gg.modl.proto.modl.v1.ActivePunishmentsResponse;
import gg.modl.proto.modl.v1.BackendNoteEntry;
import gg.modl.proto.modl.v1.PanelAddEvidenceRequest;
import gg.modl.proto.modl.v1.PanelAddModificationRequest;
import gg.modl.proto.modl.v1.PanelCreatePunishmentRequest;
import gg.modl.proto.modl.v1.PanelFindAndLinkAccountsResponse;
import gg.modl.proto.modl.v1.PanelLinkedAccountsResponse;
import gg.modl.proto.modl.v1.PanelLinkedBanEntry;
import gg.modl.proto.modl.v1.PanelLinkedBansResponse;
import gg.modl.proto.modl.v1.PlayerReplaysResponse;
import gg.modl.proto.modl.v1.PlayerSearchResultsResponse;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Transcribes the player-domain record DTOs returned by the service layer into the
 * {@code modl.v1} proto message types the panel consumes over proto-JSON. The service
 * layer keeps returning the plain records (shared with the minecraft controllers); the
 * proto boundary lives entirely here so a single shape change never leaks into the
 * domain model.
 *
 * <p>Timestamps use two representations by proto field type. Proto {@code int64} date
 * fields carry epoch milliseconds directly (proto-JSON emits the number as a string; the
 * panel converts via {@code epochToIso} in players.ts). Proto {@code string} date fields
 * carry an ISO-8601 instant (e.g. {@code 2024-06-10T...Z}); the panel passes these straight
 * to {@code new Date()}, which parses ISO but not raw epoch millis.
 */
final class PanelPlayerProtoMapper {

    private PanelPlayerProtoMapper() {
    }

    static PlayerSearchResultsResponse toPlayerSearchResults(List<PlayerSearchResult> results) {
        PlayerSearchResultsResponse.Builder builder = PlayerSearchResultsResponse.newBuilder();
        results.forEach(result -> builder.addItems(toPlayerSearchResult(result)));
        return builder.build();
    }

    static gg.modl.proto.modl.v1.PlayerSearchResult toPlayerSearchResult(PlayerSearchResult result) {
        gg.modl.proto.modl.v1.PlayerSearchResult.Builder builder = gg.modl.proto.modl.v1.PlayerSearchResult.newBuilder()
            .setUuid(nullToEmpty(result.uuid()))
            .setUsername(nullToEmpty(result.username()))
            .setStatus(nullToEmpty(result.status()))
            .setIsOnline(result.isOnline());
        if (result.lastOnline() != null) {
            builder.setLastOnline(epochString(result.lastOnline()));
        }
        return builder.build();
    }

    static gg.modl.proto.modl.v1.PlayerDetailResponse toPlayerDetail(PlayerDetailResponse detail) {
        gg.modl.proto.modl.v1.PlayerDetailResponse.Builder builder = gg.modl.proto.modl.v1.PlayerDetailResponse.newBuilder()
            .setId(nullToEmpty(detail.id()))
            .setMinecraftUuid(nullToEmpty(detail.minecraftUuid()))
            .setSocial(nullToEmpty(detail.social()))
            .setGameplay(nullToEmpty(detail.gameplay()))
            .setSocialPoints(detail.socialPoints())
            .setGameplayPoints(detail.gameplayPoints())
            .setPlaytimeHours(detail.playtimeHours());

        detail.usernames().forEach(username -> builder.addUsernames(toUsernameEntry(username)));
        detail.notes().forEach(note -> builder.addNotes(toBackendNoteEntry(note)));
        detail.ipAddresses().forEach(ip -> builder.addIpAddresses(toIpEntry(ip)));
        detail.punishments().forEach(punishment -> builder.addPunishments(toPunishment(punishment)));

        if (detail.data() != null) {
            builder.setData(ProtoMapperSupport.legacyStruct(detail.data()));
        }
        if (detail.latestIPData() != null) {
            builder.setLatestIpData(toIpEntry(detail.latestIPData()));
        }
        if (detail.lastServer() != null) {
            builder.setLastServer(detail.lastServer());
        }
        return builder.build();
    }

    static ActivePunishmentsResponse toActivePunishments(List<PunishmentResponse> punishments) {
        ActivePunishmentsResponse.Builder builder = ActivePunishmentsResponse.newBuilder();
        punishments.forEach(punishment -> builder.addItems(toPunishment(punishment)));
        return builder.build();
    }

    static gg.modl.proto.modl.v1.PunishmentResponse toPunishment(PunishmentResponse punishment) {
        gg.modl.proto.modl.v1.PunishmentResponse.Builder builder = gg.modl.proto.modl.v1.PunishmentResponse.newBuilder()
            .setId(nullToEmpty(punishment.id()))
            .setType(nullToEmpty(punishment.type()))
            .setTypeOrdinal(punishment.typeOrdinal())
            .setIssuerName(nullToEmpty(punishment.issuerName()))
            .setIsAppealable(punishment.isAppealable())
            .setActive(punishment.active());

        if (punishment.issued() != null) {
            builder.setIssued(punishment.issued().getTime());
        }
        if (punishment.started() != null) {
            builder.setStarted(punishment.started().getTime());
        }
        if (punishment.reason() != null) {
            builder.setReason(punishment.reason());
        }
        if (punishment.severity() != null) {
            builder.setSeverity(punishment.severity());
        }
        if (punishment.status() != null) {
            builder.setStatus(punishment.status());
        }
        if (punishment.expires() != null) {
            builder.setExpires(punishment.expires().getTime());
        }
        if (punishment.playerUuid() != null) {
            builder.setPlayerUuid(punishment.playerUuid());
        }
        if (punishment.playerUsername() != null) {
            builder.setPlayerUsername(punishment.playerUsername());
        }
        if (punishment.altBlocking() != null) {
            builder.setAltBlocking(punishment.altBlocking());
        }
        if (punishment.statWiping() != null) {
            builder.setStatWiping(punishment.statWiping());
        }
        if (punishment.effectiveCategory() != null) {
            builder.setEffectiveCategory(punishment.effectiveCategory());
        }

        punishment.modifications().forEach(modification -> builder.addModifications(toModification(modification)));
        punishment.notes().forEach(note -> builder.addNotes(toPunishmentNote(note)));
        punishment.evidence().forEach(evidence -> builder.addEvidence(toEvidence(evidence)));
        if (punishment.attachedTicketIds() != null) {
            builder.addAllAttachedTicketIds(punishment.attachedTicketIds());
        }
        return builder.build();
    }

    static PanelLinkedAccountsResponse toLinkedAccounts(List<LinkedAccountResponse> accounts) {
        PanelLinkedAccountsResponse.Builder builder = PanelLinkedAccountsResponse.newBuilder();
        accounts.forEach(account -> builder.addLinkedAccounts(toLinkedAccount(account)));
        return builder.build();
    }

    static PanelLinkedBansResponse toLinkedBans(List<Map<String, Object>> linkedBans) {
        PanelLinkedBansResponse.Builder builder = PanelLinkedBansResponse.newBuilder();
        for (Map<String, Object> entry : linkedBans) {
            builder.addLinkedBans(PanelLinkedBanEntry.newBuilder()
                .setPunishmentId(string(entry.get("punishmentId")))
                .setPlayerUuid(string(entry.get("playerUuid")))
                .setPlayerName(string(entry.get("playerName")))
                .setActive(Boolean.TRUE.equals(entry.get("active")))
                .build());
        }
        return builder.build();
    }

    static PanelFindAndLinkAccountsResponse toFindAndLinkResult(boolean success, String message, int linkedAccountsFound) {
        return PanelFindAndLinkAccountsResponse.newBuilder()
            .setSuccess(success)
            .setMessage(nullToEmpty(message))
            .setLinkedAccountsFound(linkedAccountsFound)
            .build();
    }

    static PlayerReplaysResponse toPlayerReplays(List<PlayerReplayResponse> replays) {
        PlayerReplaysResponse.Builder builder = PlayerReplaysResponse.newBuilder();
        replays.forEach(replay -> builder.addItems(toPlayerReplay(replay)));
        return builder.build();
    }

    static CreatePunishmentRequest fromCreatePunishment(PanelCreatePunishmentRequest request) {
        List<CreateNoteRequest> notes = request.getNotesList().stream()
            .map(note -> new CreateNoteRequest(
                note.getText(),
                emptyToNull(note.getIssuerName()),
                emptyToNull(note.getIssuerId()),
                note.hasDate() ? note.getDate() : null
            ))
            .toList();
        List<CreateEvidenceRequest> evidence = request.getEvidenceList().stream()
            .map(item -> new CreateEvidenceRequest(
                item.getText(),
                emptyToNull(item.getIssuerName()),
                emptyToNull(item.getIssuerId()),
                item.hasDate() ? item.getDate() : null,
                item.hasType() ? item.getType() : null,
                item.hasFileUrl() ? item.getFileUrl() : null,
                item.hasFileName() ? item.getFileName() : null,
                item.hasFileType() ? item.getFileType() : null,
                item.hasFileSize() ? item.getFileSize() : null
            ))
            .toList();
        return new CreatePunishmentRequest(
            emptyToNull(request.getIssuerName()),
            emptyToNull(request.getIssuerId()),
            request.getTypeOrdinal(),
            notes.isEmpty() ? null : notes,
            evidence.isEmpty() ? null : evidence,
            request.getAttachedTicketIdsList().isEmpty() ? null : List.copyOf(request.getAttachedTicketIdsList()),
            request.hasSeverity() ? request.getSeverity() : null,
            request.hasStatus() ? request.getStatus() : null,
            request.hasData() ? ProtoMapperSupport.structToMap(request.getData()) : null,
            request.hasReason() ? request.getReason() : null,
            request.hasDuration() ? request.getDuration() : null
        );
    }

    static AddModificationRequest fromAddModification(PanelAddModificationRequest request) {
        return new AddModificationRequest(
            request.getType(),
            emptyToNull(request.getIssuerName()),
            emptyToNull(request.getIssuerId()),
            request.hasEffectiveDuration() ? request.getEffectiveDuration() : null,
            request.hasReason() ? request.getReason() : null,
            request.hasAppealTicketId() ? request.getAppealTicketId() : null
        );
    }

    static AddEvidenceRequest fromAddEvidence(PanelAddEvidenceRequest request) {
        return new AddEvidenceRequest(
            request.hasText() ? request.getText() : null,
            request.getType(),
            emptyToNull(request.getIssuerName()),
            emptyToNull(request.getIssuerId()),
            request.hasUrl() ? request.getUrl() : null,
            request.hasFileName() ? request.getFileName() : null,
            request.hasFileType() ? request.getFileType() : null,
            request.hasFileSize() ? request.getFileSize() : null
        );
    }

    static ModifyPunishmentTicketsRequest fromModifyTickets(gg.modl.proto.modl.v1.ModifyPunishmentTicketsRequest request) {
        return new ModifyPunishmentTicketsRequest(
            request.getAddTicketIdsList().isEmpty() ? null : List.copyOf(request.getAddTicketIdsList()),
            request.getRemoveTicketIdsList().isEmpty() ? null : List.copyOf(request.getRemoveTicketIdsList()),
            request.getModifyAssociatedTickets(),
            emptyToNull(request.getIssuerName()),
            emptyToNull(request.getIssuerId())
        );
    }

    private static gg.modl.proto.modl.v1.PlayerReplayResponse toPlayerReplay(PlayerReplayResponse replay) {
        gg.modl.proto.modl.v1.PlayerReplayResponse.Builder builder = gg.modl.proto.modl.v1.PlayerReplayResponse.newBuilder()
            .setReplayId(nullToEmpty(replay.replayId()))
            .setTargetUuid(nullToEmpty(replay.targetUuid()))
            .setTargetName(nullToEmpty(replay.targetName()))
            .setMcVersion(nullToEmpty(replay.mcVersion()))
            .setFileSize(replay.fileSize())
            .setStatus(nullToEmpty(replay.status()))
            .setReplayUrl(nullToEmpty(replay.replayUrl()))
            .setMatchSource(replay.matchSource() != null ? replay.matchSource().name() : "");
        if (replay.createdAt() != null) {
            builder.setCreatedAt(replay.createdAt().getTime());
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.LinkedAccountResponse toLinkedAccount(LinkedAccountResponse account) {
        gg.modl.proto.modl.v1.LinkedAccountResponse.Builder builder = gg.modl.proto.modl.v1.LinkedAccountResponse.newBuilder()
            .setMinecraftUuid(nullToEmpty(account.minecraftUuid()))
            .setUsername(nullToEmpty(account.username()))
            .setActiveBans(account.activeBans())
            .setActiveMutes(account.activeMutes());
        if (account.lastLinkedUpdate() != null) {
            builder.setLastLinkedUpdate(epochString(account.lastLinkedUpdate()));
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.UsernameEntry toUsernameEntry(UsernameEntry username) {
        return gg.modl.proto.modl.v1.UsernameEntry.newBuilder()
            .setUsername(nullToEmpty(username.username()))
            .setDate(username.date() != null ? epochString(username.date()) : "")
            .build();
    }

    private static BackendNoteEntry toBackendNoteEntry(NoteEntry note) {
        return BackendNoteEntry.newBuilder()
            .setId(nullToEmpty(note.getId()))
            .setText(nullToEmpty(note.getText()))
            .setDate(note.getDate() != null ? epochString(note.getDate()) : "")
            .setIssuerName(nullToEmpty(note.getIssuerName()))
            .setIssuerId(nullToEmpty(note.getIssuerId()))
            .build();
    }

    private static gg.modl.proto.modl.v1.IPEntry toIpEntry(IPEntry ip) {
        gg.modl.proto.modl.v1.IPEntry.Builder builder = gg.modl.proto.modl.v1.IPEntry.newBuilder()
            .setProxy(ip.isProxy())
            .setHosting(ip.isHosting())
            .setFirstLogin(ip.getFirstLogin() != null ? epochString(ip.getFirstLogin()) : "");
        if (ip.getIpAddress() != null) {
            builder.setIpAddress(ip.getIpAddress());
        }
        if (ip.getCountry() != null) {
            builder.setCountry(ip.getCountry());
        }
        if (ip.getRegion() != null) {
            builder.setRegion(ip.getRegion());
        }
        if (ip.getAsn() != null) {
            builder.setAsn(ip.getAsn());
        }
        if (ip.getLogins() != null) {
            ip.getLogins().forEach(login -> builder.addLogins(login != null ? epochString(login) : ""));
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.PunishmentModification toModification(PunishmentModification modification) {
        gg.modl.proto.modl.v1.PunishmentModification.Builder builder = gg.modl.proto.modl.v1.PunishmentModification.newBuilder()
            .setId(nullToEmpty(modification.id()))
            .setType(nullToEmpty(modification.type()))
            .setReason(nullToEmpty(modification.reason()));
        if (modification.date() != null) {
            builder.setDate(modification.date().getTime());
        }
        if (modification.issuerName() != null) {
            builder.setIssuerName(modification.issuerName());
        }
        if (modification.issuerId() != null) {
            builder.setIssuerId(modification.issuerId());
        }
        if (modification.effectiveDuration() != null) {
            builder.setEffectiveDuration(modification.effectiveDuration());
        }
        if (modification.appealTicketId() != null) {
            builder.setAppealTicketId(modification.appealTicketId());
        }
        if (modification.data() != null) {
            builder.setData(ProtoMapperSupport.legacyStruct(modification.data()));
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.PunishmentNote toPunishmentNote(PunishmentNote note) {
        gg.modl.proto.modl.v1.PunishmentNote.Builder builder = gg.modl.proto.modl.v1.PunishmentNote.newBuilder()
            .setId(nullToEmpty(note.id()))
            .setText(nullToEmpty(note.text()));
        if (note.date() != null) {
            builder.setDate(note.date().getTime());
        }
        if (note.issuerName() != null) {
            builder.setIssuerName(note.issuerName());
        }
        if (note.issuerId() != null) {
            builder.setIssuerId(note.issuerId());
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.PunishmentEvidence toEvidence(PunishmentEvidence evidence) {
        gg.modl.proto.modl.v1.PunishmentEvidence.Builder builder = gg.modl.proto.modl.v1.PunishmentEvidence.newBuilder()
            .setType(nullToEmpty(evidence.type()));
        if (evidence.text() != null) {
            builder.setText(evidence.text());
        }
        if (evidence.url() != null) {
            builder.setUrl(evidence.url());
        }
        if (evidence.uploadedBy() != null) {
            builder.setUploadedBy(evidence.uploadedBy());
        }
        if (evidence.uploadedById() != null) {
            builder.setUploadedById(evidence.uploadedById());
        }
        if (evidence.uploadedAt() != null) {
            builder.setUploadedAt(evidence.uploadedAt().getTime());
        }
        if (evidence.fileName() != null) {
            builder.setFileName(evidence.fileName());
        }
        if (evidence.fileType() != null) {
            builder.setFileType(evidence.fileType());
        }
        if (evidence.fileSize() != null) {
            builder.setFileSize(evidence.fileSize());
        }
        return builder.build();
    }

    private static String epochString(Date date) {
        return java.time.Instant.ofEpochMilli(date.getTime()).toString();
    }

    private static String string(Object value) {
        return value == null ? "" : Objects.toString(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
