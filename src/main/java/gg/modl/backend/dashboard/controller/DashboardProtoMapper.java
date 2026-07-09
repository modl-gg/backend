package gg.modl.backend.dashboard.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;

import gg.modl.backend.dashboard.dto.response.ActivityItemResponse;
import gg.modl.backend.dashboard.dto.response.DashboardMetricsResponse;
import gg.modl.backend.dashboard.dto.response.RecentPunishmentResponse;
import gg.modl.backend.dashboard.dto.response.RecentTicketResponse;
import gg.modl.proto.modl.v1.ActivityAction;
import gg.modl.proto.modl.v1.DashboardActivityResponse;
import gg.modl.proto.modl.v1.DashboardRecentPunishmentsResponse;
import gg.modl.proto.modl.v1.DashboardRecentTicketsResponse;
import java.util.List;

final class DashboardProtoMapper {

    private DashboardProtoMapper() {
    }

    static gg.modl.proto.modl.v1.DashboardMetricsResponse toMetrics(DashboardMetricsResponse metrics) {
        return gg.modl.proto.modl.v1.DashboardMetricsResponse.newBuilder()
            .setTotalTickets(metrics.totalTickets())
            .setOpenTickets(metrics.openTickets())
            .setTotalPlayers(metrics.totalPlayers())
            .setTotalPunishments(metrics.totalPunishments())
            .setActivePunishments(metrics.activePunishments())
            .setTotalStaff(metrics.totalStaff())
            .setTicketsTrend(metrics.ticketsTrend())
            .setPlayersTrend(metrics.playersTrend())
            .build();
    }

    static DashboardRecentTicketsResponse toRecentTickets(List<RecentTicketResponse> tickets) {
        DashboardRecentTicketsResponse.Builder builder = DashboardRecentTicketsResponse.newBuilder();
        tickets.forEach(ticket -> builder.addTickets(toRecentTicket(ticket)));
        return builder.build();
    }

    static DashboardRecentPunishmentsResponse toRecentPunishments(List<RecentPunishmentResponse> punishments) {
        DashboardRecentPunishmentsResponse.Builder builder = DashboardRecentPunishmentsResponse.newBuilder();
        punishments.forEach(punishment -> builder.addPunishments(toRecentPunishment(punishment)));
        return builder.build();
    }

    static DashboardActivityResponse toActivity(List<ActivityItemResponse> activities) {
        DashboardActivityResponse.Builder builder = DashboardActivityResponse.newBuilder();
        activities.forEach(activity -> builder.addActivity(toActivityItem(activity)));
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.RecentTicketResponse toRecentTicket(RecentTicketResponse ticket) {
        return gg.modl.proto.modl.v1.RecentTicketResponse.newBuilder()
            .setId(stringValue(ticket.id()))
            .setTitle(stringValue(ticket.title()))
            .setInitialMessage(stringValue(ticket.initialMessage()))
            .setStatus(stringValue(ticket.status()))
            .setPriority(stringValue(ticket.priority()))
            .setCreatedAt(longValue(ticket.createdAt()))
            .setPlayerName(stringValue(ticket.playerName()))
            .setType(stringValue(ticket.type()))
            .build();
    }

    private static gg.modl.proto.modl.v1.RecentPunishmentResponse toRecentPunishment(RecentPunishmentResponse punishment) {
        return gg.modl.proto.modl.v1.RecentPunishmentResponse.newBuilder()
            .setId(stringValue(punishment.id()))
            .setPlayerName(stringValue(punishment.playerName()))
            .setPlayerUuid(stringValue(punishment.playerUuid()))
            .setType(stringValue(punishment.type()))
            .setReason(stringValue(punishment.reason()))
            .setIssuerName(stringValue(punishment.issuerName()))
            .setIssued(longValue(punishment.issued()))
            .setActive(punishment.active())
            .build();
    }

    private static gg.modl.proto.modl.v1.ActivityItemResponse toActivityItem(ActivityItemResponse activity) {
        gg.modl.proto.modl.v1.ActivityItemResponse.Builder builder =
            gg.modl.proto.modl.v1.ActivityItemResponse.newBuilder()
            .setId(stringValue(activity.id()))
            .setType(stringValue(activity.type()))
            .setColor(stringValue(activity.color()))
            .setTitle(stringValue(activity.title()))
            .setTime(longValue(activity.time()))
            .setDescription(stringValue(activity.description()));
        if (activity.actions() != null) {
            activity.actions().forEach(action -> builder.addActions(ActivityAction.newBuilder()
                .setLabel(stringValue(action.label()))
                .setLink(stringValue(action.link()))
                .setPrimary(action.primary())
                .build()));
        }
        return builder.build();
    }
}
