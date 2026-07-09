package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.data.SecurityEvent;
import gg.modl.proto.modl.v1.AdminSecurityEvent;
import gg.modl.proto.modl.v1.AdminSecurityEventsData;
import gg.modl.proto.modl.v1.AdminSecurityEventsResponse;
import gg.modl.proto.modl.v1.AdminSecurityLast24Hours;
import gg.modl.proto.modl.v1.AdminSecurityLast7Days;
import gg.modl.proto.modl.v1.AdminSecurityPagination;
import gg.modl.proto.modl.v1.AdminSecuritySummaryData;
import gg.modl.proto.modl.v1.AdminSecuritySummaryResponse;

import java.util.Map;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.list;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.map;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

final class AdminSecurityProtoMapper {

    private AdminSecurityProtoMapper() {
    }

    static AdminSecurityEventsResponse toEventsResponse(Map<String, Object> response) {
        Map<String, Object> data = map(response.get("data"));
        Map<String, Object> pagination = map(data.get("pagination"));

        AdminSecurityEventsData.Builder dataBuilder = AdminSecurityEventsData.newBuilder()
            .setPagination(AdminSecurityPagination.newBuilder()
                .setPage((int) longValue(pagination.get("page")))
                .setLimit((int) longValue(pagination.get("limit")))
                .setTotal(longValue(pagination.get("total")))
                .setPages((int) longValue(pagination.get("pages")))
                .build());
        list(data.get("events")).stream()
            .filter(SecurityEvent.class::isInstance)
            .map(SecurityEvent.class::cast)
            .forEach(event -> dataBuilder.addEvents(toEvent(event)));
        return AdminSecurityEventsResponse.newBuilder()
            .setSuccess(true)
            .setData(dataBuilder.build())
            .build();
    }

    static AdminSecuritySummaryResponse toSummaryResponse(Map<String, Object> response) {
        Map<String, Object> data = map(response.get("data"));
        Map<String, Object> last24Hours = map(data.get("last24Hours"));
        Map<String, Object> last7Days = map(data.get("last7Days"));

        AdminSecuritySummaryData.Builder dataBuilder = AdminSecuritySummaryData.newBuilder()
            .setLast24Hours(AdminSecurityLast24Hours.newBuilder()
                .setCritical(longValue(last24Hours.get("critical")))
                .setHigh(longValue(last24Hours.get("high")))
                .setMedium(longValue(last24Hours.get("medium")))
                .build())
            .setLast7Days(AdminSecurityLast7Days.newBuilder()
                .setTotal(longValue(last7Days.get("total")))
                .build());
        Object timestamp = data.get("timestamp");
        if (timestamp != null) {
            dataBuilder.setTimestamp(toTimestamp(timestamp));
        }
        return AdminSecuritySummaryResponse.newBuilder()
            .setSuccess(true)
            .setData(dataBuilder.build())
            .build();
    }

    private static AdminSecurityEvent toEvent(SecurityEvent event) {
        AdminSecurityEvent.Builder builder = AdminSecurityEvent.newBuilder()
            .setId(stringValue(event.getId()))
            .setType(stringValue(event.getType()))
            .setSeverity(stringValue(event.getSeverity()))
            .setSource(stringValue(event.getSource()))
            .setDescription(stringValue(event.getDescription()));
        if (event.getTimestamp() != null) {
            builder.setTimestamp(toTimestamp(event.getTimestamp()));
        }
        return builder.build();
    }
}
