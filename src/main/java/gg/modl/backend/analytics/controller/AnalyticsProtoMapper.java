package gg.modl.backend.analytics.controller;

import gg.modl.backend.analytics.dto.response.AuditLogsAnalyticsResponse;
import gg.modl.backend.analytics.dto.response.OverviewResponse;
import gg.modl.backend.analytics.dto.response.PlayerActivityResponse;
import gg.modl.backend.analytics.dto.response.PunishmentAnalyticsResponse;
import gg.modl.backend.analytics.dto.response.TicketAnalyticsResponse;
import gg.modl.proto.modl.v1.AnalyticsOverviewResponse;

final class AnalyticsProtoMapper {

    private AnalyticsProtoMapper() {
    }

    static AnalyticsOverviewResponse toOverviewResponse(OverviewResponse overview) {
        return AnalyticsOverviewResponse.newBuilder()
            .putOverview("overview", toOverview(overview))
            .build();
    }

    private static gg.modl.proto.modl.v1.OverviewResponse toOverview(OverviewResponse overview) {
        return gg.modl.proto.modl.v1.OverviewResponse.newBuilder()
            .setTotalTickets(overview.totalTickets())
            .setTotalPlayers(overview.totalPlayers())
            .setTotalStaff(overview.totalStaff())
            .setActiveTickets(overview.activeTickets())
            .setTicketChange(overview.ticketChange())
            .setPlayerChange(overview.playerChange())
            .build();
    }

    static gg.modl.proto.modl.v1.TicketAnalyticsResponse toTicketAnalytics(TicketAnalyticsResponse analytics) {
        gg.modl.proto.modl.v1.TicketAnalyticsResponse.Builder builder =
            gg.modl.proto.modl.v1.TicketAnalyticsResponse.newBuilder();
        analytics.byStatus().forEach(item -> builder.addByStatus(
            gg.modl.proto.modl.v1.TicketAnalyticsResponse.StatusCount.newBuilder()
                .setStatus(item.status())
                .setCount(item.count())
                .build()));
        analytics.byCategory().forEach(item -> builder.addByCategory(
            gg.modl.proto.modl.v1.TicketAnalyticsResponse.CategoryCount.newBuilder()
                .setCategory(item.category())
                .setCount(item.count())
                .build()));
        analytics.avgResolutionByCategory().forEach(item -> builder.addAvgResolutionByCategory(
            gg.modl.proto.modl.v1.TicketAnalyticsResponse.CategoryResolutionTime.newBuilder()
                .setCategory(item.category())
                .setAvgHours(item.avgHours())
                .build()));
        analytics.dailyTickets().forEach(item -> builder.addDailyTickets(
            gg.modl.proto.modl.v1.TicketAnalyticsResponse.DailyTicket.newBuilder()
                .setDate(item.date())
                .setCount(item.count())
                .build()));
        return builder.build();
    }

    static gg.modl.proto.modl.v1.PunishmentAnalyticsResponse toPunishmentAnalytics(PunishmentAnalyticsResponse analytics) {
        gg.modl.proto.modl.v1.PunishmentAnalyticsResponse.Builder builder =
            gg.modl.proto.modl.v1.PunishmentAnalyticsResponse.newBuilder();
        analytics.byType().forEach(item -> builder.addByType(
            gg.modl.proto.modl.v1.PunishmentAnalyticsResponse.TypeCount.newBuilder()
                .setType(item.type())
                .setCount(item.count())
                .build()));
        analytics.dailyPunishments().forEach(item -> builder.addDailyPunishments(
            gg.modl.proto.modl.v1.PunishmentAnalyticsResponse.DailyPunishment.newBuilder()
                .setDate(item.date())
                .setCount(item.count())
                .build()));
        analytics.byStaff().forEach(item -> builder.addByStaff(
            gg.modl.proto.modl.v1.PunishmentAnalyticsResponse.StaffPunishment.newBuilder()
                .setUsername(item.username())
                .setCount(item.count())
                .build()));
        return builder.build();
    }

    static gg.modl.proto.modl.v1.AuditLogsAnalyticsResponse toAuditLogsAnalytics(AuditLogsAnalyticsResponse analytics) {
        gg.modl.proto.modl.v1.AuditLogsAnalyticsResponse.Builder builder =
            gg.modl.proto.modl.v1.AuditLogsAnalyticsResponse.newBuilder();
        analytics.byLevel().forEach(item -> builder.addByLevel(
            gg.modl.proto.modl.v1.AuditLogsAnalyticsResponse.LevelCount.newBuilder()
                .setLevel(item.level())
                .setCount(item.count())
                .build()));
        analytics.hourlyTrend().forEach(item -> builder.addHourlyTrend(
            gg.modl.proto.modl.v1.AuditLogsAnalyticsResponse.HourlyCount.newBuilder()
                .setHour(item.hour())
                .setCount(item.count())
                .build()));
        return builder.build();
    }

    static gg.modl.proto.modl.v1.PlayerActivityResponse toPlayerActivity(PlayerActivityResponse analytics) {
        gg.modl.proto.modl.v1.PlayerActivityResponse.Builder builder =
            gg.modl.proto.modl.v1.PlayerActivityResponse.newBuilder();
        analytics.newPlayersTrend().forEach(item -> builder.addNewPlayersTrend(
            gg.modl.proto.modl.v1.PlayerActivityResponse.DailyCount.newBuilder()
                .setDate(item.date())
                .setCount(item.count())
                .build()));
        analytics.loginsByCountry().forEach(item -> builder.addLoginsByCountry(
            gg.modl.proto.modl.v1.PlayerActivityResponse.CountryCount.newBuilder()
                .setCountry(item.country())
                .setCount(item.count())
                .build()));
        PlayerActivityResponse.SuspiciousActivity suspicious = analytics.suspiciousActivity();
        if (suspicious != null) {
            builder.setSuspiciousActivity(
                gg.modl.proto.modl.v1.PlayerActivityResponse.SuspiciousActivity.newBuilder()
                    .setProxyCount(suspicious.proxyCount())
                    .setHostingCount(suspicious.hostingCount())
                    .build());
        }
        return builder.build();
    }
}
