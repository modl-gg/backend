package gg.modl.backend.admin.service;

import gg.modl.backend.admin.data.SecurityEvent;
import gg.modl.backend.admin.dto.response.AdminPagination;
import gg.modl.backend.admin.dto.response.AdminSecurityEvents;
import gg.modl.backend.admin.dto.response.AdminSecuritySummary;
import gg.modl.backend.database.mongo.repository.SecurityEventMongoRepository;
import gg.modl.backend.infrastructure.util.DateRangeUtil;
import gg.modl.backend.infrastructure.util.PaginationHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminSecurityService {
    private final SecurityEventMongoRepository securityEventRepository;

    public AdminSecurityEvents getSecurityEvents(
        int page,
        int limit,
        String type,
        String severity,
        String source,
        String search,
        String startDate,
        String endDate
    ) {
        int pageNum = PaginationHelper.normalizePage(page);
        int limitNum = PaginationHelper.normalizeLimit(limit, 100);
        int skip = PaginationHelper.calculateSkip(page, limitNum);
        Date start = DateRangeUtil.parseEpochMillis(startDate);
        Date end = DateRangeUtil.parseEpochMillis(endDate);

        List<SecurityEvent> events = securityEventRepository.findSecurityEvents(type, severity, source, search, start, end, skip, limitNum);
        long total = securityEventRepository.countSecurityEvents(type, severity, source, search, start, end);

        return new AdminSecurityEvents(
            events,
            new AdminPagination(pageNum, limitNum, total, PaginationHelper.calculateTotalPages(total, limitNum)));
    }

    public AdminSecuritySummary getSecuritySummary() {
        Date last24h = Date.from(Instant.now().minus(24, ChronoUnit.HOURS));
        Date last7d = Date.from(Instant.now().minus(7, ChronoUnit.DAYS));

        long criticalEvents24h = securityEventRepository.countBySeveritySince("critical", last24h);
        long highEvents24h = securityEventRepository.countBySeveritySince("high", last24h);
        long mediumEvents24h = securityEventRepository.countBySeveritySince("medium", last24h);
        long totalEvents7d = securityEventRepository.countSince(last7d);

        return new AdminSecuritySummary(
            new AdminSecuritySummary.Last24Hours(criticalEvents24h, highEvents24h, mediumEvents24h),
            totalEvents7d,
            new Date());
    }
}
