package gg.modl.backend.analytics.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.modl.backend.analytics.dto.response.AuditLogsAnalyticsResponse;
import gg.modl.backend.analytics.dto.response.OverviewResponse;
import gg.modl.backend.analytics.dto.response.PlayerActivityResponse;
import gg.modl.backend.analytics.dto.response.PunishmentAnalyticsResponse;
import gg.modl.backend.analytics.dto.response.TicketAnalyticsResponse;
import gg.modl.backend.database.mongo.repository.AnalyticsMongoRepository;
import gg.modl.backend.database.mongo.repository.AnalyticsMongoRepository.IdCountResult;
import gg.modl.backend.database.mongo.repository.AnalyticsMongoRepository.PlayerActivityFacet;
import gg.modl.backend.database.mongo.repository.AnalyticsMongoRepository.PunishmentAnalyticsFacet;
import gg.modl.backend.player.service.IssuerNameResolver;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.PunishmentTypeIndex;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.service.StaffService;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketStatus;
import gg.modl.backend.infrastructure.proto.ProtoMapperSupport;
import gg.modl.backend.infrastructure.util.DateRangeUtil;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {
    private static final String ANALYTICS_TIME_ZONE = "UTC";
    private static final double MILLIS_PER_HOUR = 1000.0 * 60 * 60;

    private final AnalyticsMongoRepository analyticsRepository;
    private final PunishmentTypeService punishmentTypeService;
    private final IssuerNameResolver issuerNameResolver;
    private final StaffService staffService;

    private final Cache<String, OverviewResponse> overviewCache = analyticsResultCache();
    private final Cache<String, TicketAnalyticsResponse> ticketAnalyticsCache = analyticsResultCache();
    private final Cache<String, PunishmentAnalyticsResponse> punishmentAnalyticsCache = analyticsResultCache();
    private final Cache<String, AuditLogsAnalyticsResponse> auditLogsAnalyticsCache = analyticsResultCache();
    private final Cache<String, PlayerActivityResponse> playerActivityCache = analyticsResultCache();

    private static <V> Cache<String, V> analyticsResultCache() {
        return Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(500)
            .build();
    }

    private static String cacheKey(Server server, String period) {
        return server.getId() + ":" + period;
    }

    @NotNull
    public OverviewResponse getOverview(@NotNull Server server) {
        return overviewCache.get(server.getId(), key -> computeOverview(server));
    }

    private OverviewResponse computeOverview(Server server) {
        final Date thirtyDaysAgo = DateRangeUtil.daysAgo(30);
        final Date sixtyDaysAgo = DateRangeUtil.daysAgo(60);
        final AnalyticsMongoRepository.OverviewStats stats = analyticsRepository.loadOverviewStats(server, thirtyDaysAgo, sixtyDaysAgo);

        final int ticketChange = stats.previousTickets() > 0
                                 ? (int) Math.round(((double) (stats.recentTickets() - stats.previousTickets()) / stats.previousTickets()) * 100)
                                 : 0;
        final int playerChange = stats.previousPlayers() > 0
                                 ? (int) Math.round(((double) (stats.recentPlayers() - stats.previousPlayers()) / stats.previousPlayers()) * 100)
                                 : 0;

        return new OverviewResponse(
            stats.totalTickets(),
            stats.totalPlayers(),
            staffService.countStaffIncludingSuperAdmin(server),
            stats.activeTickets(),
            ticketChange,
            playerChange
        );
    }

    @NotNull
    public TicketAnalyticsResponse getTicketAnalytics(@NotNull Server server, @NotNull String period) {
        return ticketAnalyticsCache.get(cacheKey(server, period), key -> computeTicketAnalytics(server, period));
    }

    private TicketAnalyticsResponse computeTicketAnalytics(Server server, String period) {
        final Date startDate = DateRangeUtil.getStartDate(period);
        final List<IdCountResult> statusResults = analyticsRepository.aggregateTicketStatusCounts(server, startDate);
        final List<TicketAnalyticsResponse.StatusCount> byStatus = statusResults.stream()
            .map(result -> new TicketAnalyticsResponse.StatusCount(normalizeStatus(result.id()), result.count()))
            .toList();

        final List<IdCountResult> categoryResults = analyticsRepository.aggregateTicketCategoryCounts(server, startDate);
        final List<TicketAnalyticsResponse.CategoryCount> byCategory = categoryResults.stream()
            .map(result -> new TicketAnalyticsResponse.CategoryCount(normalizeCategory(result.id()), result.count()))
            .toList();

        final List<TicketAnalyticsResponse.DailyTicket> dailyTickets = analyticsRepository.aggregateDailyTicketCounts(server, startDate, ANALYTICS_TIME_ZONE)
            .stream()
            .map(result -> new TicketAnalyticsResponse.DailyTicket(result.id(), result.count()))
            .toList();

        final List<TicketAnalyticsResponse.CategoryResolutionTime> avgResolution =
            analyticsRepository.aggregateAvgResolutionByCategory(server, startDate).stream()
                .map(result -> new TicketAnalyticsResponse.CategoryResolutionTime(
                    normalizeCategory(result.id()),
                    result.avgMillis() / MILLIS_PER_HOUR))
                .toList();

        return new TicketAnalyticsResponse(byStatus, byCategory, avgResolution, dailyTickets);
    }

    private String normalizeCategory(String category) {
        try {
            return TicketCategory.fromCanonicalId(category).getDisplayName();
        } catch (IllegalArgumentException ignored) {
            return "Other";
        }
    }

    private String normalizeStatus(String status) {
        try {
            return TicketStatus.fromCanonicalId(status).getDisplayName();
        } catch (IllegalArgumentException ignored) {
            return "Other";
        }
    }

    @NotNull
    public PunishmentAnalyticsResponse getPunishmentAnalytics(@NotNull Server server, @NotNull String period) {
        return punishmentAnalyticsCache.get(cacheKey(server, period), key -> computePunishmentAnalytics(server, period));
    }

    private PunishmentAnalyticsResponse computePunishmentAnalytics(Server server, String period) {
        Date startDate = DateRangeUtil.getStartDate(period);
        PunishmentAnalyticsFacet facet = analyticsRepository.aggregatePunishmentAnalytics(server, startDate, ANALYTICS_TIME_ZONE);
        if (facet == null) {
            return new PunishmentAnalyticsResponse(List.of(), List.of(), List.of());
        }
        Map<Integer, String> punishmentTypeNames = resolvePunishmentTypeNames(server);

        List<PunishmentAnalyticsResponse.TypeCount> byType = facet.byType().stream()
            .map(entry -> {
                Integer typeOrdinal = entry.typeOrdinal();
                String typeName = typeOrdinal != null
                                  ? punishmentTypeNames.getOrDefault(typeOrdinal, "Unknown")
                                  : "Unknown";
                return new PunishmentAnalyticsResponse.TypeCount(typeName, entry.count());
            })
            .sorted((a, b) -> Integer.compare(b.count(), a.count()))
            .toList();

        Set<String> potentialIds = new HashSet<>();
        for (var entry : facet.byStaff()) {
            if (entry.issuerId() != null && !entry.issuerId().isBlank()) {
                potentialIds.add(entry.issuerId());
            }
        }
        Map<String, String> resolvedStaff = issuerNameResolver.batchResolve(potentialIds, server);

        Map<String, Integer> staffCountMap = new HashMap<>();
        for (var entry : facet.byStaff()) {
            String staffName;
            if (entry.issuerId() != null && resolvedStaff.containsKey(entry.issuerId())) {
                staffName = resolvedStaff.get(entry.issuerId());
            } else {
                staffName = normalizeStaffName(entry.issuerId());
            }
            staffCountMap.merge(staffName, entry.count(), Integer::sum);
        }
        List<PunishmentAnalyticsResponse.StaffPunishment> byStaff = staffCountMap.entrySet()
            .stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(20)
            .map(entry -> new PunishmentAnalyticsResponse.StaffPunishment(entry.getKey(), entry.getValue()))
            .toList();

        Map<String, Integer> dailyPunishmentMap = new LinkedHashMap<>();
        for (var entry : facet.daily()) {
            dailyPunishmentMap.merge(entry.date(), entry.count(), Integer::sum);
        }
        List<PunishmentAnalyticsResponse.DailyPunishment> dailyPunishments = dailyPunishmentMap.entrySet()
            .stream()
            .map(entry -> new PunishmentAnalyticsResponse.DailyPunishment(entry.getKey(), entry.getValue()))
            .toList();

        return new PunishmentAnalyticsResponse(byType, dailyPunishments, byStaff);
    }

    private Map<Integer, String> resolvePunishmentTypeNames(Server server) {
        Map<Integer, String> typeNames = new HashMap<>();
        PunishmentTypeIndex.byOrdinal(punishmentTypeService.getPunishmentTypes(server))
            .forEach((ordinal, type) -> typeNames.put(ordinal, type.getName()));
        return typeNames;
    }

    private String normalizeStaffName(Object rawStaffName) {
        if (rawStaffName == null) {
            return "Unknown";
        }

        String normalized = rawStaffName.toString().trim();
        return normalized.isBlank() ? "Unknown" : normalized;
    }

    @NotNull
    public AuditLogsAnalyticsResponse getAuditLogsAnalytics(@NotNull Server server, @NotNull String period) {
        return auditLogsAnalyticsCache.get(cacheKey(server, period), key -> computeAuditLogsAnalytics(server, period));
    }

    private AuditLogsAnalyticsResponse computeAuditLogsAnalytics(Server server, String period) {
        Date startDate = DateRangeUtil.getStartDate(period);
        List<IdCountResult> levelResults = analyticsRepository.aggregateAuditLogLevelCounts(server, startDate);
        List<AuditLogsAnalyticsResponse.LevelCount> byLevel = levelResults.stream()
            .map(result -> new AuditLogsAnalyticsResponse.LevelCount(
                result.id() != null ? result.id() : "unknown",
                result.count()
            ))
            .toList();

        long now = System.currentTimeMillis();
        Date since = DateRangeUtil.daysAgo(1);

        List<Document> hourlyResults = analyticsRepository.aggregateHourlyAuditLogCounts(server, since, ANALYTICS_TIME_ZONE);

        Map<String, Integer> hourlyMap = new LinkedHashMap<>();
        for (Document doc : hourlyResults) {
            String bucketKey = doc.getString("_id");
            if (bucketKey == null) {
                continue;
            }
            hourlyMap.merge(bucketKey, ProtoMapperSupport.intValueOrZero(doc.get("count")), Integer::sum);
        }

        ZoneId zone = ZoneId.of(ANALYTICS_TIME_ZONE);
        DateTimeFormatter bucketFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH").withZone(zone);
        List<AuditLogsAnalyticsResponse.HourlyCount> hourlyTrend = new ArrayList<>();
        for (int i = 23; i >= 0; i--) {
            ZonedDateTime zdt = Instant.ofEpochMilli(now - (long) i * 60 * 60 * 1000).atZone(zone);
            String bucketKey = bucketFmt.format(zdt);
            String hourLabel = String.format("%02d:00", zdt.getHour());
            int count = hourlyMap.getOrDefault(bucketKey, 0);
            hourlyTrend.add(new AuditLogsAnalyticsResponse.HourlyCount(hourLabel, count));
        }

        return new AuditLogsAnalyticsResponse(byLevel, hourlyTrend);
    }

    @NotNull
    public PlayerActivityResponse getPlayerActivityAnalytics(@NotNull Server server, @NotNull String period) {
        return playerActivityCache.get(cacheKey(server, period), key -> computePlayerActivityAnalytics(server, period));
    }

    private PlayerActivityResponse computePlayerActivityAnalytics(Server server, String period) {
        Date startDate = DateRangeUtil.getStartDate(period);

        PlayerActivityFacet facet = analyticsRepository.aggregatePlayerActivity(server, startDate, ANALYTICS_TIME_ZONE);
        if (facet == null) {
            return new PlayerActivityResponse(List.of(), List.of(),
                new PlayerActivityResponse.SuspiciousActivity(0, 0));
        }

        List<PlayerActivityResponse.DailyCount> newPlayersTrend = facet.newPlayers().stream()
            .map(entry -> new PlayerActivityResponse.DailyCount(entry.date(), entry.count()))
            .toList();

        List<PlayerActivityResponse.CountryCount> loginsByCountry = facet.byCountry().stream()
            .map(entry -> new PlayerActivityResponse.CountryCount(entry.country(), entry.count()))
            .toList();

        return new PlayerActivityResponse(
            newPlayersTrend,
            loginsByCountry,
            new PlayerActivityResponse.SuspiciousActivity(
                facet.suspicious().proxyCount(),
                facet.suspicious().hostingCount())
        );
    }

}
