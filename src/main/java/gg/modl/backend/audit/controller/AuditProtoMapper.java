package gg.modl.backend.audit.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.listOfMaps;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toStruct;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.audit.dto.response.ActivePunishmentResponse;
import gg.modl.backend.audit.dto.response.PunishmentAuditResponse;
import gg.modl.backend.audit.dto.response.StaffDetailsResponse;
import gg.modl.backend.audit.dto.response.StaffPerformanceResponse;
import gg.modl.proto.modl.v1.ActivePunishmentsAuditResponse;
import gg.modl.proto.modl.v1.AuditBulkOperationResponse;
import gg.modl.proto.modl.v1.AuditDatabaseTableResponse;
import gg.modl.proto.modl.v1.AuditRollbackResponse;
import com.google.protobuf.Timestamp;
import gg.modl.proto.modl.v1.PunishmentAuditListResponse;
import gg.modl.proto.modl.v1.StaffPerformanceListResponse;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

final class AuditProtoMapper {

    private AuditProtoMapper() {
    }

    @Nullable
    static Date toDate(Timestamp timestamp) {
        if (timestamp == null || (timestamp.getSeconds() == 0 && timestamp.getNanos() == 0)) {
            return null;
        }
        return new Date(timestamp.getSeconds() * 1000L + timestamp.getNanos() / 1_000_000L);
    }

    private static void setTimestamp(Consumer<Timestamp> setter, @Nullable Date date) {
        if (date != null) {
            setter.accept(toTimestamp(date));
        }
    }

    static StaffPerformanceListResponse toStaffPerformanceList(List<StaffPerformanceResponse> performance) {
        StaffPerformanceListResponse.Builder builder = StaffPerformanceListResponse.newBuilder();
        performance.forEach(item -> builder.addStaff(toStaffPerformance(item)));
        return builder.build();
    }

    static gg.modl.proto.modl.v1.StaffDetailsResponse toStaffDetails(StaffDetailsResponse details) {
        gg.modl.proto.modl.v1.StaffDetailsResponse.Builder builder =
            gg.modl.proto.modl.v1.StaffDetailsResponse.newBuilder()
                .setUsername(stringValue(details.username()))
                .setPeriod(stringValue(details.period()))
                .setEvidenceUploads(details.evidenceUploads());
        details.punishments().forEach(punishment -> {
            gg.modl.proto.modl.v1.StaffDetailsResponse.PunishmentDetail.Builder detail =
                gg.modl.proto.modl.v1.StaffDetailsResponse.PunishmentDetail.newBuilder()
                    .setId(stringValue(punishment.id()))
                    .setPlayerId(stringValue(punishment.playerId()))
                    .setPlayerName(stringValue(punishment.playerName()))
                    .setType(stringValue(punishment.type()))
                    .setReason(stringValue(punishment.reason()))
                    .setDuration(stringValue(punishment.duration()))
                    .setActive(punishment.active())
                    .setRolledBack(punishment.rolledBack());
            setTimestamp(detail::setIssued, punishment.issued());
            builder.addPunishments(detail.build());
        });
        details.tickets().forEach(ticket -> {
            gg.modl.proto.modl.v1.StaffDetailsResponse.TicketDetail.Builder detail =
                gg.modl.proto.modl.v1.StaffDetailsResponse.TicketDetail.newBuilder()
                    .setId(stringValue(ticket.id()))
                    .setSubject(stringValue(ticket.subject()))
                    .setCategory(stringValue(ticket.category()))
                    .setStatus(stringValue(ticket.status()))
                    .setResponseTime(ticket.responseTime());
            setTimestamp(detail::setLastActivity, ticket.lastActivity());
            builder.addTickets(detail.build());
        });
        details.dailyActivity().forEach(activity -> builder.addDailyActivity(
            gg.modl.proto.modl.v1.StaffDetailsResponse.DailyActivity.newBuilder()
                .setDate(stringValue(activity.date()))
                .setPunishments(activity.punishments())
                .setTickets(activity.tickets())
                .setEvidence(activity.evidence())
                .build()));
        details.punishmentTypeBreakdown().forEach(breakdown -> builder.addPunishmentTypeBreakdown(
            gg.modl.proto.modl.v1.StaffDetailsResponse.PunishmentTypeBreakdown.newBuilder()
                .setType(stringValue(breakdown.type()))
                .setCount(breakdown.count())
                .build()));
        StaffDetailsResponse.Summary summary = details.summary();
        if (summary != null) {
            builder.setSummary(gg.modl.proto.modl.v1.StaffDetailsResponse.Summary.newBuilder()
                .setTotalPunishments(summary.totalPunishments())
                .setTotalTickets(summary.totalTickets())
                .setAvgResponseTime(summary.avgResponseTime())
                .setEvidenceUploads(summary.evidenceUploads())
                .build());
        }
        return builder.build();
    }

    static ActivePunishmentsAuditResponse toActivePunishments(List<ActivePunishmentResponse> punishments) {
        ActivePunishmentsAuditResponse.Builder builder = ActivePunishmentsAuditResponse.newBuilder();
        punishments.forEach(punishment -> builder.addPunishments(toActivePunishment(punishment)));
        return builder.build();
    }

    static PunishmentAuditListResponse toPunishmentAuditList(List<PunishmentAuditResponse> punishments) {
        PunishmentAuditListResponse.Builder builder = PunishmentAuditListResponse.newBuilder();
        punishments.forEach(punishment -> builder.addPunishments(toPunishmentAudit(punishment)));
        return builder.build();
    }

    static AuditRollbackResponse toRollbackResponse(boolean success, String message) {
        return AuditRollbackResponse.newBuilder()
            .setSuccess(success)
            .setMessage(stringValue(message))
            .build();
    }

    static AuditBulkOperationResponse toBulkOperationResponse(boolean success, int count, String message) {
        return AuditBulkOperationResponse.newBuilder()
            .setSuccess(success)
            .setCount(count)
            .setMessage(stringValue(message))
            .build();
    }

    static AuditDatabaseTableResponse toDatabaseTableResponse(Map<String, Object> result) {
        AuditDatabaseTableResponse.Builder builder = AuditDatabaseTableResponse.newBuilder()
            .setTotal(longValue(result.get("total")))
            .setLimit((int) longValue(result.get("limit")))
            .setSkip((int) longValue(result.get("skip")));
        listOfMaps(result.get("data")).forEach(row -> builder.addData(toStruct(row)));
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.StaffPerformanceResponse toStaffPerformance(StaffPerformanceResponse item) {
        gg.modl.proto.modl.v1.StaffPerformanceResponse.Builder builder =
            gg.modl.proto.modl.v1.StaffPerformanceResponse.newBuilder()
                .setId(stringValue(item.id()))
                .setUsername(stringValue(item.username()))
                .setRole(stringValue(item.role()))
                .setTotalActions(item.totalActions())
                .setTicketResponses(item.ticketResponses())
                .setPunishmentsIssued(item.punishmentsIssued())
                .setAvgResponseTime(item.avgResponseTime());
        setTimestamp(builder::setLastActive, item.lastActive());
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.ActivePunishmentResponse toActivePunishment(ActivePunishmentResponse punishment) {
        gg.modl.proto.modl.v1.ActivePunishmentResponse.Builder builder =
            gg.modl.proto.modl.v1.ActivePunishmentResponse.newBuilder()
                .setId(stringValue(punishment.id()))
                .setPlayerId(stringValue(punishment.playerId()))
                .setPlayerName(stringValue(punishment.playerName()))
                .setType(stringValue(punishment.type()))
                .setTypeOrdinal(punishment.typeOrdinal())
                .setCategory(stringValue(punishment.category()))
                .setStaffName(stringValue(punishment.staffName()))
                .setReason(stringValue(punishment.reason()))
                .setActive(punishment.active())
                .setHasEvidence(punishment.hasEvidence())
                // protobuf suffixes this scalar accessor with its field number: the repeated `evidence`
                // field already generates getEvidenceCount() for its size, so evidence_count collides.
                .setEvidenceCount15(punishment.evidenceCount());
        setTimestamp(builder::setIssued, punishment.issued());
        setTimestamp(builder::setStarted, punishment.started());
        setTimestamp(builder::setExpires, punishment.expires());
        if (punishment.duration() != null) {
            builder.setDuration(punishment.duration());
        }
        if (punishment.evidence() != null) {
            punishment.evidence().forEach(evidence -> builder.addEvidence16(
                gg.modl.proto.modl.v1.ActivePunishmentResponse.EvidenceItem.newBuilder()
                    .setText(stringValue(evidence.text()))
                    .setUrl(stringValue(evidence.url()))
                    .setType(stringValue(evidence.type()))
                    .setFileName(stringValue(evidence.fileName()))
                    .build()));
        }
        if (punishment.attachedTicketIds() != null) {
            builder.addAllAttachedTicketIds(punishment.attachedTicketIds());
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.PunishmentAuditResponse toPunishmentAudit(PunishmentAuditResponse punishment) {
        gg.modl.proto.modl.v1.PunishmentAuditResponse.Builder builder =
            gg.modl.proto.modl.v1.PunishmentAuditResponse.newBuilder()
                .setId(stringValue(punishment.id()))
                .setType(stringValue(punishment.type()))
                .setPlayerId(stringValue(punishment.playerId()))
                .setPlayerName(stringValue(punishment.playerName()))
                .setStaffId(stringValue(punishment.staffId()))
                .setStaffName(stringValue(punishment.staffName()))
                .setReason(stringValue(punishment.reason()))
                .setDuration(stringValue(punishment.duration()))
                .setCanRollback(punishment.canRollback());
        setTimestamp(builder::setTimestamp, punishment.timestamp());
        return builder.build();
    }
}
