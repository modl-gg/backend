package gg.modl.backend.player.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.booleanValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.dateAwareString;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.intValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.legacyStruct;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.list;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.listOf;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.listOfMaps;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.map;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalInt;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalLong;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalString;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringObjectMap;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;

import gg.modl.backend.infrastructure.proto.ProtoMapperSupport;
import gg.modl.backend.player.dto.response.CreateNoteResult;
import gg.modl.backend.player.dto.response.LinkedAccountsResult;
import gg.modl.backend.player.dto.response.OnlinePlayersResult;
import gg.modl.backend.player.dto.response.PaginatedNotesResult;
import gg.modl.backend.player.dto.response.PaginatedPunishmentsResult;
import gg.modl.backend.player.dto.response.PardonResult;
import gg.modl.backend.player.dto.response.PlayerFetchResult;
import gg.modl.backend.player.dto.response.PlayerLookupResult;
import gg.modl.backend.player.dto.response.PlayerLoginResult;
import gg.modl.backend.player.dto.response.PlayerReportsResult;
import gg.modl.backend.player.dto.response.PunishmentView;
import gg.modl.backend.player.dto.response.SimplePunishmentView;
import gg.modl.proto.modl.v1.Account;
import gg.modl.proto.modl.v1.IPEntry;
import gg.modl.proto.modl.v1.LinkedAccountsResponse;
import gg.modl.proto.modl.v1.NoteEntry;
import gg.modl.proto.modl.v1.OnlinePlayersResponse;
import gg.modl.proto.modl.v1.PaginatedNotesResponse;
import gg.modl.proto.modl.v1.PaginatedPunishmentsResponse;
import gg.modl.proto.modl.v1.PardonResponse;
import gg.modl.proto.modl.v1.PendingStatWipe;
import gg.modl.proto.modl.v1.PlayerGetResponse;
import gg.modl.proto.modl.v1.PlayerLoginResponse;
import gg.modl.proto.modl.v1.PlayerLookupResponse;
import gg.modl.proto.modl.v1.PlayerNameResponse;
import gg.modl.proto.modl.v1.PlayerNoteCreateResponse;
import gg.modl.proto.modl.v1.PlayerProfileResponse;
import gg.modl.proto.modl.v1.PunishmentEvidence;
import gg.modl.proto.modl.v1.PunishmentListEntry;
import gg.modl.proto.modl.v1.PunishmentModification;
import gg.modl.proto.modl.v1.PunishmentNote;
import gg.modl.proto.modl.v1.PunishmentResponse;
import gg.modl.proto.modl.v1.ReportEntry;
import gg.modl.proto.modl.v1.ReportsResponse;
import gg.modl.proto.modl.v1.SimpleResponse;
import gg.modl.proto.modl.v1.SimplePunishment;
import gg.modl.proto.modl.v1.UsernameEntry;
import java.util.Map;
import java.util.Objects;

public final class MinecraftPlayerProtoMapper {
    private MinecraftPlayerProtoMapper() {
    }

    static PlayerLoginResponse toPlayerLoginResponse(PlayerLoginResult result) {
        PlayerLoginResponse.Builder response = PlayerLoginResponse.newBuilder()
            .setStatus(result.status());

        result.activePunishments().stream()
            .map(MinecraftPlayerProtoMapper::toSimplePunishment)
            .forEach(response::addActivePunishments);

        result.pendingNotifications().stream()
            .map(ProtoMapperSupport::legacyStruct)
            .forEach(response::addPendingNotifications);

        result.pendingIpLookups().forEach(response::addPendingIpLookups);

        result.pendingStatWipes().stream()
            .map(MinecraftPlayerProtoMapper::toPendingStatWipe)
            .forEach(response::addPendingStatWipes);

        return response.build();
    }

    static SimpleResponse toSimpleResponse(boolean success) {
        return SimpleResponse.newBuilder()
            .setSuccess(success)
            .build();
    }

    static OnlinePlayersResponse toOnlinePlayersResponse(OnlinePlayersResult result) {
        OnlinePlayersResponse.Builder response = OnlinePlayersResponse.newBuilder()
            .setStatus(200);

        result.players().stream()
            .map(MinecraftPlayerProtoMapper::toOnlinePlayer)
            .forEach(response::addPlayers);

        return response.build();
    }

    static PlayerProfileResponse toPlayerProfileResponse(Map<String, Object> profile) {
        PlayerProfileResponse.Builder response = PlayerProfileResponse.newBuilder()
            .setStatus(200)
            .setProfile(toAccount(profile));

        setOptionalInt(response::setPunishmentCount, profile.get("punishmentCount"));
        setOptionalInt(response::setNoteCount, profile.get("noteCount"));
        return response.build();
    }

    static PlayerGetResponse toPlayerGetResponse(PlayerFetchResult.Found found) {
        return PlayerGetResponse.newBuilder()
            .setStatus(200)
            .setMessage(stringValue(found.message()))
            .setPlayer(toAccount(found.player()))
            .build();
    }

    static PlayerNameResponse toPlayerNameResponse(PlayerFetchResult.Found found) {
        return PlayerNameResponse.newBuilder()
            .setStatus(200)
            .setMessage(stringValue(found.message()))
            .setPlayer(toAccount(found.player()))
            .build();
    }

    static PlayerLookupResponse toPlayerLookupResponse(PlayerLookupResult.Found found) {
        return PlayerLookupResponse.newBuilder()
            .setStatus(200)
            .setMessage(stringValue(found.message()))
            .setData(toPlayerLookupData(found.data()))
            .build();
    }

    static PlayerNoteCreateResponse toPlayerNoteCreateResponse(CreateNoteResult result) {
        return switch (result) {
            case CreateNoteResult.Created created -> PlayerNoteCreateResponse.newBuilder()
                .setStatus(200)
                .setMessage(stringValue(created.message()))
                .setSuccess(true)
                .build();
            case CreateNoteResult.NotFound notFound -> PlayerNoteCreateResponse.newBuilder()
                .setStatus(404)
                .setMessage(stringValue(notFound.message()))
                .setSuccess(false)
                .build();
        };
    }

    static LinkedAccountsResponse toLinkedAccountsResponse(LinkedAccountsResult.Found found) {
        LinkedAccountsResponse.Builder response = LinkedAccountsResponse.newBuilder()
            .setStatus(200)
            .setPage(intValue(found.page()))
            .setHasMore(booleanValue(found.hasMore()));

        setOptionalInt(response::setTotalCount, found.totalCount());
        found.linkedAccounts().stream()
            .map(MinecraftPlayerProtoMapper::toAccount)
            .forEach(response::addLinkedAccounts);

        return response.build();
    }

    static PaginatedPunishmentsResponse toPaginatedPunishmentsResponse(PaginatedPunishmentsResult.Found found) {
        PaginatedPunishmentsResponse.Builder response = PaginatedPunishmentsResponse.newBuilder()
            .setStatus(200)
            .setTotalCount(found.totalCount())
            .setPage(found.page())
            .setHasMore(found.hasMore());

        found.punishments().stream()
            .map(MinecraftPlayerProtoMapper::toPunishmentListEntry)
            .forEach(response::addPunishments);

        return response.build();
    }

    static PaginatedNotesResponse toPaginatedNotesResponse(PaginatedNotesResult.Found found) {
        PaginatedNotesResponse.Builder response = PaginatedNotesResponse.newBuilder()
            .setStatus(200)
            .setTotalCount(found.totalCount())
            .setPage(found.page())
            .setHasMore(found.hasMore());

        found.notes().stream()
            .map(MinecraftPlayerProtoMapper::toNoteEntry)
            .forEach(response::addNotes);

        return response.build();
    }

    static ReportsResponse toReportsResponse(PlayerReportsResult result) {
        ReportsResponse.Builder response = ReportsResponse.newBuilder()
            .setStatus(200);

        result.reports().stream()
            .map(MinecraftPlayerProtoMapper::toReportEntry)
            .forEach(response::addReports);

        return response.build();
    }

    static PardonResponse toPardonResponse(PardonResult result) {
        return switch (result) {
            case PardonResult.Pardoned pardoned -> PardonResponse.newBuilder()
                .setStatus(200)
                .setSuccess(pardoned.success())
                .setPardonedCount(pardoned.pardonedCount())
                .setMessage(stringValue(pardoned.message()))
                .build();
            case PardonResult.PlayerNotFound notFound -> PardonResponse.newBuilder()
                .setStatus(404)
                .setSuccess(false)
                .setPardonedCount(0)
                .setMessage(stringValue(notFound.message()))
                .build();
        };
    }

    public static SimplePunishment toSimplePunishment(SimplePunishmentView view) {
        if (view == null) {
            return SimplePunishment.getDefaultInstance();
        }

        SimplePunishment.Builder builder = SimplePunishment.newBuilder()
            .setType(stringValue(view.type()))
            .setDescription(stringValue(view.description()))
            .setId(stringValue(view.id()))
            .setStarted(view.started())
            .setOrdinal(view.ordinal());

        setOptionalString(builder::setCategory, view.category());
        setOptionalLong(builder::setExpiration, view.expiration());
        setOptionalString(builder::setIssuerName, view.issuerName());
        setOptionalLong(builder::setIssuedAt, view.issuedAt());
        setOptionalString(builder::setPlayerDescription, view.playerDescription());
        return builder.build();
    }

    private static PendingStatWipe toPendingStatWipe(Map<String, Object> wipe) {
        return PendingStatWipe.newBuilder()
            .setMinecraftUuid(stringValue(wipe.get("minecraftUuid")))
            .setUsername(stringValue(wipe.get("username")))
            .setPunishmentId(stringValue(wipe.get("punishmentId")))
            .build();
    }

    private static OnlinePlayersResponse.OnlinePlayer toOnlinePlayer(Map<String, Object> player) {
        OnlinePlayersResponse.OnlinePlayer.Builder builder = OnlinePlayersResponse.OnlinePlayer.newBuilder()
            .setUuid(stringValue(player.get("uuid")))
            .setUsername(stringValue(player.get("username")))
            .setJoinedAt(dateAwareString(player.get("joinedAt")));

        setOptionalLong(builder::setTotalPlaytimeMs, player.get("totalPlaytimeMs"));
        return builder.build();
    }

    private static Account toAccount(Map<String, Object> account) {
        Account.Builder builder = Account.newBuilder()
            .setId(stringValue(account.get("id")))
            .setMinecraftUuid(stringValue(account.get("minecraftUuid")));

        listOfMaps(account.get("usernames")).stream()
            .map(MinecraftPlayerProtoMapper::toUsernameEntry)
            .forEach(builder::addUsernames);

        listOfMaps(account.get("notes")).stream()
            .map(MinecraftPlayerProtoMapper::toNoteEntry)
            .forEach(builder::addNotes);

        listOfMaps(account.get("ipAddresses")).stream()
            .map(MinecraftPlayerProtoMapper::toIpEntry)
            .forEach(builder::addIpAddresses);

        listOf(account.get("punishments"), PunishmentView.class).stream()
            .map(MinecraftPlayerProtoMapper::toPunishmentResponse)
            .forEach(builder::addPunishments);

        listOfMaps(account.get("pendingNotifications")).stream()
            .map(ProtoMapperSupport::legacyStruct)
            .forEach(builder::addPendingNotifications);

        Object data = account.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            builder.setData(legacyStruct(stringObjectMap(dataMap)));
        }

        return builder.build();
    }

    private static UsernameEntry toUsernameEntry(Map<String, Object> username) {
        return UsernameEntry.newBuilder()
            .setUsername(stringValue(username.get("username")))
            .setDate(dateAwareString(username.get("date")))
            .build();
    }

    private static NoteEntry toNoteEntry(Map<String, Object> note) {
        NoteEntry.Builder builder = NoteEntry.newBuilder()
            .setText(stringValue(note.get("text")))
            .setDate(dateAwareString(note.get("date")))
            .setIssuerName(stringValue(note.get("issuerName")))
            .setIssuerId(stringValue(note.get("issuerId")));
        Object id = note.get("id");
        if (id != null) {
            builder.setId(Objects.toString(id));
        }
        return builder.build();
    }

    private static IPEntry toIpEntry(Map<String, Object> ip) {
        IPEntry.Builder builder = IPEntry.newBuilder()
            .setProxy(booleanValue(ip.get("proxy")))
            .setHosting(booleanValue(ip.get("hosting")))
            .setFirstLogin(dateAwareString(ip.get("firstLogin")));

        setOptionalString(builder::setIpAddress, ip.get("ipAddress"));
        setOptionalString(builder::setCountry, ip.get("country"));
        setOptionalString(builder::setRegion, ip.get("region"));
        setOptionalString(builder::setAsn, ip.get("asn"));

        list(ip.get("logins")).stream()
            .map(ProtoMapperSupport::dateAwareString)
            .forEach(builder::addLogins);

        return builder.build();
    }

    private static PunishmentResponse toPunishmentResponse(PunishmentView punishment) {
        PunishmentResponse.Builder builder = PunishmentResponse.newBuilder()
            .setId(stringValue(punishment.id()))
            .setType(stringValue(punishment.type()))
            .setTypeOrdinal(punishment.typeOrdinal())
            .setIssuerName(stringValue(punishment.issuerName()))
            .setIssued(longValue(punishment.issued()));

        setOptionalLong(builder::setStarted, punishment.started());
        setOptionalString(builder::setPlayerUuid, punishment.playerUuid());

        punishment.attachedTicketIds().forEach(builder::addAttachedTicketIds);

        return builder.build();
    }

    private static PunishmentListEntry toPunishmentListEntry(PunishmentView punishment) {
        PunishmentListEntry.Builder builder = PunishmentListEntry.newBuilder()
            .setId(stringValue(punishment.id()))
            .setIssuerName(stringValue(punishment.issuerName()))
            .setIssued(longValue(punishment.issued()))
            .setType(stringValue(punishment.type()));

        setOptionalLong(builder::setStarted, punishment.started());
        builder.setTypeOrdinal(punishment.typeOrdinal());

        punishment.modifications().stream()
            .map(MinecraftPlayerProtoMapper::toPunishmentModification)
            .forEach(builder::addModifications);

        punishment.notes().stream()
            .map(MinecraftPlayerProtoMapper::toPunishmentNote)
            .forEach(builder::addNotes);

        punishment.evidence().stream()
            .map(MinecraftPlayerProtoMapper::toPunishmentEvidence)
            .forEach(builder::addEvidence);

        punishment.attachedTicketIds().forEach(builder::addAttachedTicketIds);

        if (punishment.data() != null) {
            builder.setData(legacyStruct(punishment.data()));
        }

        return builder.build();
    }

    static PunishmentModification toPunishmentModification(Map<String, Object> modification) {
        PunishmentModification.Builder builder = PunishmentModification.newBuilder()
            .setId(stringValue(modification.get("id")))
            .setType(stringValue(modification.get("type")))
            .setDate(longValue(modification.get("date")))
            .setReason(stringValue(modification.get("reason")));

        setOptionalString(builder::setIssuerName, modification.get("issuerName"));
        setOptionalString(builder::setIssuerId, modification.get("issuerId"));
        setOptionalLong(builder::setEffectiveDuration, modification.get("effectiveDuration"));
        setOptionalString(builder::setAppealTicketId, modification.get("appealTicketId"));
        Object data = modification.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            builder.setData(legacyStruct(stringObjectMap(dataMap)));
        }

        return builder.build();
    }

    static PunishmentNote toPunishmentNote(Map<String, Object> note) {
        PunishmentNote.Builder builder = PunishmentNote.newBuilder()
            .setId(stringValue(note.get("id")))
            .setText(stringValue(note.get("text")))
            .setDate(longValue(note.get("date")));

        setOptionalString(builder::setIssuerName, note.get("issuerName"));
        setOptionalString(builder::setIssuerId, note.get("issuerId"));
        return builder.build();
    }

    static PunishmentEvidence toPunishmentEvidence(Map<String, Object> evidence) {
        PunishmentEvidence.Builder builder = PunishmentEvidence.newBuilder()
            .setType(stringValue(evidence.get("type")))
            .setUploadedAt(longValue(evidence.get("uploadedAt")));

        setOptionalString(builder::setText, evidence.get("text"));
        setOptionalString(builder::setUrl, evidence.get("url"));
        setOptionalString(builder::setUploadedBy, evidence.get("uploadedBy"));
        setOptionalString(builder::setUploadedById, evidence.get("uploadedById"));
        setOptionalString(builder::setFileName, evidence.get("fileName"));
        setOptionalString(builder::setFileType, evidence.get("fileType"));
        setOptionalLong(builder::setFileSize, evidence.get("fileSize"));
        return builder.build();
    }

    private static ReportEntry toReportEntry(Map<String, Object> report) {
        ReportEntry.Builder builder = ReportEntry.newBuilder()
            .setId(stringValue(report.get("id")))
            .setType(stringValue(report.get("type")))
            .setCategory(stringValue(report.get("category")))
            .setReporterName(stringValue(report.get("reporterName")))
            .setReporterUuid(stringValue(report.get("reporterUuid")))
            .setReportedPlayerUuid(stringValue(report.get("reportedPlayerUuid")))
            .setReportedPlayerName(stringValue(report.get("reportedPlayerName")))
            .setSubject(stringValue(report.get("subject")))
            .setContent(stringValue(report.get("content")))
            .setStatus(stringValue(report.get("status")))
            .setPriority(stringValue(report.get("priority")))
            .setCreatedAt(longValue(report.get("createdAt")));

        list(report.get("assignedTo")).stream()
            .map(Objects::toString)
            .forEach(builder::addAssignedTo);

        listOfMaps(report.get("chatMessages")).stream()
            .map(ProtoMapperSupport::legacyStruct)
            .forEach(builder::addChatMessages);

        setOptionalString(builder::setReplayUrl, report.get("replayUrl"));
        return builder.build();
    }

    private static PlayerLookupResponse.PlayerLookupData toPlayerLookupData(Map<String, Object> data) {
        PlayerLookupResponse.PlayerLookupData.Builder builder = PlayerLookupResponse.PlayerLookupData.newBuilder()
            .setMinecraftUuid(stringValue(data.get("minecraftUuid")))
            .setCurrentUsername(stringValue(data.get("currentUsername")))
            .setFirstSeen(dateAwareString(data.get("firstSeen")))
            .setLastSeen(dateAwareString(data.get("lastSeen")))
            .setCurrentServer(stringValue(data.get("currentServer")))
            .setIpAddress(stringValue(data.get("ipAddress")))
            .setCountry(stringValue(data.get("country")))
            .setProfileUrl(stringValue(data.get("profileUrl")))
            .setPunishmentsUrl(stringValue(data.get("punishmentsUrl")))
            .setTicketsUrl(stringValue(data.get("ticketsUrl")))
            .setPunishmentStats(toPlayerLookupPunishmentStats(map(data.get("punishmentStats"))))
            .setIsOnline(booleanValue(data.get("isOnline")));

        list(data.get("previousUsernames")).stream()
            .map(Objects::toString)
            .forEach(builder::addPreviousUsernames);

        listOfMaps(data.get("recentPunishments")).stream()
            .map(MinecraftPlayerProtoMapper::toPlayerLookupRecentPunishment)
            .forEach(builder::addRecentPunishments);

        listOfMaps(data.get("recentTickets")).stream()
            .map(MinecraftPlayerProtoMapper::toPlayerLookupRecentTicket)
            .forEach(builder::addRecentTickets);

        return builder.build();
    }

    private static PlayerLookupResponse.PlayerLookupPunishmentStats toPlayerLookupPunishmentStats(Map<String, Object> stats) {
        return PlayerLookupResponse.PlayerLookupPunishmentStats.newBuilder()
            .setStatus(stringValue(stats.get("status")))
            .setTotalPunishments(intValue(stats.get("totalPunishments")))
            .setActivePunishments(intValue(stats.get("activePunishments")))
            .setBans(intValue(stats.get("bans")))
            .setMutes(intValue(stats.get("mutes")))
            .setKicks(intValue(stats.get("kicks")))
            .setWarnings(intValue(stats.get("warnings")))
            .setPoints(intValue(stats.get("points")))
            .build();
    }

    private static PlayerLookupResponse.PlayerLookupRecentPunishment toPlayerLookupRecentPunishment(Map<String, Object> punishment) {
        return PlayerLookupResponse.PlayerLookupRecentPunishment.newBuilder()
            .setId(stringValue(punishment.get("id")))
            .setType(stringValue(punishment.get("type")))
            .setIssuer(stringValue(punishment.get("issuer")))
            .setIssuedAt(dateAwareString(punishment.get("issuedAt")))
            .setExpiresAt(dateAwareString(punishment.get("expiresAt")))
            .setIsActive(booleanValue(punishment.get("isActive")))
            .build();
    }

    private static PlayerLookupResponse.PlayerLookupRecentTicket toPlayerLookupRecentTicket(Map<String, Object> ticket) {
        return PlayerLookupResponse.PlayerLookupRecentTicket.newBuilder()
            .setId(stringValue(ticket.get("id")))
            .setTitle(stringValue(ticket.get("title")))
            .setCategory(stringValue(ticket.get("category")))
            .setStatus(stringValue(ticket.get("status")))
            .setCreatedAt(dateAwareString(ticket.get("createdAt")))
            .setLastUpdated(dateAwareString(ticket.get("lastUpdated")))
            .build();
    }
}
