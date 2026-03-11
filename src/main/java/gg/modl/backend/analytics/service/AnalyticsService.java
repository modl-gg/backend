package gg.modl.backend.analytics.service;

import gg.modl.backend.analytics.dto.response.AuditLogsAnalyticsResponse;
import gg.modl.backend.analytics.dto.response.OverviewResponse;
import gg.modl.backend.analytics.dto.response.PlayerActivityResponse;
import gg.modl.backend.analytics.dto.response.PunishmentAnalyticsResponse;
import gg.modl.backend.analytics.dto.response.TicketAnalyticsResponse;
import gg.modl.backend.database.mongo.repository.AnalyticsMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.player.service.IssuerNameResolver;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketStatus;
import gg.modl.backend.util.DateRangeUtil;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {
    private final AnalyticsMongoRepository analyticsRepository;
    private final PunishmentTypeService punishmentTypeService;
    private final IssuerNameResolver issuerNameResolver;
    private final StaffMongoRepository staffRepository;
    private static final String ANALYTICS_TIME_ZONE = TimeZone.getDefault().getID();

    public OverviewResponse getOverview(Server server) {
        long now = System.currentTimeMillis();
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        Date thirtyDaysAgo = new Date(now - thirtyDaysMs);
        Date sixtyDaysAgo = new Date(now - 2 * thirtyDaysMs);
        AnalyticsMongoRepository.OverviewStats stats = analyticsRepository.loadOverviewStats(server, thirtyDaysAgo, sixtyDaysAgo);

        int ticketChange = stats.previousTickets() > 0
                           ? (int) Math.round(((double) (stats.recentTickets() - stats.previousTickets()) / stats.previousTickets()) * 100)
                           : 0;
        int playerChange = 0;

        return new OverviewResponse(
            stats.totalTickets(),
            stats.totalPlayers(),
            stats.totalStaff(),
            stats.activeTickets(),
            ticketChange,
            playerChange
        );
    }

    public TicketAnalyticsResponse getTicketAnalytics(Server server, String period) {
        Date startDate = DateRangeUtil.getStartDate(period);
        List<Document> statusResults = analyticsRepository.aggregateTicketStatusCounts(server, startDate);
        List<TicketAnalyticsResponse.StatusCount> byStatus = statusResults.stream()
            .map(doc -> new TicketAnalyticsResponse.StatusCount(normalizeStatus(doc.getString("_id")), doc.getInteger("count", 0)))
            .toList();

        List<Document> categoryResults = analyticsRepository.aggregateTicketCategoryCounts(server, startDate);
        List<TicketAnalyticsResponse.CategoryCount> byCategory = categoryResults.stream()
            .map(doc -> new TicketAnalyticsResponse.CategoryCount(normalizeCategory(doc.getString("_id")), doc.getInteger("count", 0)))
            .toList();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd");
        List<TicketAnalyticsResponse.DailyTicket> dailyTickets = analyticsRepository.aggregateDailyTicketCounts(server, startDate)
            .stream()
            .map(doc -> new TicketAnalyticsResponse.DailyTicket(
                formatDateLabel(doc.getString("_id"), dateFormatter),
                toInt(doc.get("count"))
            ))
            .toList();

        List<TicketAnalyticsResponse.CategoryResolutionTime> avgResolution = Collections.emptyList();

        return new TicketAnalyticsResponse(byStatus, byCategory, avgResolution, dailyTickets);
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String formatDateLabel(String dateKey, DateTimeFormatter formatter) {
        if (dateKey == null || dateKey.isBlank()) {
            return "Unknown";
        }

        try {
            return LocalDate.parse(dateKey).format(formatter);
        } catch (Exception ignored) {
            return dateKey;
        }
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

    public PunishmentAnalyticsResponse getPunishmentAnalytics(Server server, String period) {
        Date startDate = DateRangeUtil.getStartDate(period);
        Document facetResults = analyticsRepository.aggregatePunishmentAnalytics(server, startDate, ANALYTICS_TIME_ZONE);
        if (facetResults == null) {
            return new PunishmentAnalyticsResponse(List.of(), List.of(), List.of(), List.of());
        }
        Map<Integer, String> punishmentTypeNames = resolvePunishmentTypeNames(server);

        List<PunishmentAnalyticsResponse.TypeCount> byType = toDocumentList(facetResults.get("byType")).stream()
            .map(doc -> {
                Object rawTypeOrdinal = doc.get("_id");
                Integer typeOrdinal = rawTypeOrdinal instanceof Number number ? number.intValue() : null;
                String typeName = typeOrdinal != null
                                  ? punishmentTypeNames.getOrDefault(typeOrdinal, "Unknown")
                                  : "Unknown";
                return new PunishmentAnalyticsResponse.TypeCount(typeName, toInt(doc.get("count")));
            })
            .sorted((a, b) -> Integer.compare(b.count(), a.count()))
            .toList();

        // Collect potential staff IDs for batch resolution
        List<Document> byStaffDocs = toDocumentList(facetResults.get("byStaff"));
        Set<String> potentialIds = new HashSet<>();
        for (Document document : byStaffDocs) {
            Object rawId = document.get("_id");
            if (rawId instanceof String s && !s.isBlank()) {
                potentialIds.add(s);
            }
        }
        Map<String, String> resolvedStaff = issuerNameResolver.batchResolve(potentialIds, server, staffRepository);

        Map<String, Integer> staffCountMap = new HashMap<>();
        for (Document document : byStaffDocs) {
            Object rawId = document.get("_id");
            String staffName;
            if (rawId instanceof String s && resolvedStaff.containsKey(s)) {
                staffName = resolvedStaff.get(s);
            } else {
                staffName = normalizeStaffName(rawId);
            }
            staffCountMap.merge(staffName, toInt(document.get("count")), Integer::sum);
        }
        List<PunishmentAnalyticsResponse.StaffPunishment> byStaff = staffCountMap.entrySet()
            .stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(20)
            .map(entry -> new PunishmentAnalyticsResponse.StaffPunishment(entry.getKey(), entry.getValue()))
            .toList();

        Map<String, Integer> dailyPunishmentMap = new LinkedHashMap<>();
        for (Document document : toDocumentList(facetResults.get("daily"))) {
            String dayLabel = formatPunishmentDay(document.getString("_id"));
            dailyPunishmentMap.merge(dayLabel, toInt(document.get("count")), Integer::sum);
        }
        List<PunishmentAnalyticsResponse.DailyPunishment> dailyPunishments = dailyPunishmentMap.entrySet()
            .stream()
            .map(entry -> new PunishmentAnalyticsResponse.DailyPunishment(entry.getKey(), entry.getValue()))
            .toList();

        return new PunishmentAnalyticsResponse(byType, List.of(), dailyPunishments, byStaff);
    }

    private Map<Integer, String> resolvePunishmentTypeNames(Server server) {
        Map<Integer, String> typeNames = new HashMap<>();
        punishmentTypeService.getPunishmentTypes(server).forEach(type ->
            typeNames.put(type.getOrdinal(), type.getName())
        );
        return typeNames;
    }

    private List<Document> toDocumentList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }

        List<Document> documents = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Document document) {
                documents.add(document);
            }
        }
        return documents;
    }

    private String normalizeStaffName(Object rawStaffName) {
        if (rawStaffName == null) {
            return "Unknown";
        }

        String normalized = rawStaffName.toString().trim();
        return normalized.isBlank() ? "Unknown" : normalized;
    }

    private String formatPunishmentDay(String dateKey) {
        if (dateKey == null || dateKey.isBlank()) {
            return "Unknown";
        }

        try {
            return LocalDate.parse(dateKey).format(DateTimeFormatter.ofPattern("MMM dd"));
        } catch (Exception ignored) {
            return dateKey;
        }
    }

    public AuditLogsAnalyticsResponse getAuditLogsAnalytics(Server server, String period) {
        Date startDate = DateRangeUtil.getStartDate(period);
        List<Document> levelResults = analyticsRepository.aggregateAuditLogLevelCounts(server, startDate);
        List<AuditLogsAnalyticsResponse.LevelCount> byLevel = levelResults.stream()
            .map(doc -> new AuditLogsAnalyticsResponse.LevelCount(
                doc.getString("_id") != null ? doc.getString("_id") : "unknown",
                doc.getInteger("count", 0)
            ))
            .toList();

        long now = System.currentTimeMillis();
        long twentyFourHoursMs = 24 * 60 * 60 * 1000L;
        Date since = new Date(now - twentyFourHoursMs);

        List<Document> hourlyResults = analyticsRepository.aggregateHourlyAuditLogCounts(server, since, ANALYTICS_TIME_ZONE);

        Map<String, Integer> hourlyMap = new LinkedHashMap<>();
        for (Document doc : hourlyResults) {
            String bucketKey = doc.getString("_id");
            int count = toInt(doc.get("count"));
            try {
                LocalDateTime ldt = LocalDateTime.parse(bucketKey, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH"));
                String hourLabel = String.format("%02d:00", ldt.getHour());
                hourlyMap.merge(hourLabel, count, Integer::sum);
            } catch (Exception ignored) {
                // skip malformed entries
            }
        }

        ZoneId zone = ZoneId.of(ANALYTICS_TIME_ZONE);
        List<AuditLogsAnalyticsResponse.HourlyCount> hourlyTrend = new ArrayList<>();
        for (int i = 23; i >= 0; i--) {
            Instant hourEnd = Instant.ofEpochMilli(now - (long) i * 60 * 60 * 1000);
            int hour = hourEnd.atZone(zone).getHour();
            String hourLabel = String.format("%02d:00", hour);
            int count = hourlyMap.getOrDefault(hourLabel, 0);
            hourlyTrend.add(new AuditLogsAnalyticsResponse.HourlyCount(hourLabel, count));
        }

        return new AuditLogsAnalyticsResponse(byLevel, hourlyTrend);
    }

    public PlayerActivityResponse getPlayerActivityAnalytics(Server server, String period) {
        Date startDate = DateRangeUtil.getStartDate(period);

        Document facetResults = analyticsRepository.aggregatePlayerActivity(server, startDate, ANALYTICS_TIME_ZONE);
        if (facetResults == null) {
            return new PlayerActivityResponse(List.of(), List.of(),
                new PlayerActivityResponse.SuspiciousActivity(0, 0));
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd");

        List<PlayerActivityResponse.DailyCount> newPlayersTrend = toDocumentList(facetResults.get("newPlayers")).stream()
            .map(doc -> new PlayerActivityResponse.DailyCount(
                formatDateLabel(doc.getString("_id"), dateFormatter),
                toInt(doc.get("count"))))
            .toList();

        List<PlayerActivityResponse.CountryCount> loginsByCountry = toDocumentList(facetResults.get("byCountry")).stream()
            .map(doc -> new PlayerActivityResponse.CountryCount(
                doc.getString("_id"),
                toInt(doc.get("count"))))
            .toList();

        List<Document> suspiciousList = toDocumentList(facetResults.get("suspicious"));
        int proxyCount = 0;
        int hostingCount = 0;
        if (!suspiciousList.isEmpty()) {
            Document suspicious = suspiciousList.getFirst();
            proxyCount = toInt(suspicious.get("proxyCount"));
            hostingCount = toInt(suspicious.get("hostingCount"));
        }

        return new PlayerActivityResponse(
            newPlayersTrend,
            loginsByCountry,
            new PlayerActivityResponse.SuspiciousActivity(proxyCount, hostingCount)
        );
    }

}
