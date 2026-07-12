package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.data.SecurityEvent;
import gg.modl.backend.admin.dto.response.AdminPagination;
import gg.modl.backend.admin.dto.response.AdminSecurityEvents;
import gg.modl.backend.admin.dto.response.AdminSecuritySummary;
import gg.modl.proto.modl.v1.AdminSecurityEvent;
import gg.modl.proto.modl.v1.AdminSecurityEventsData;
import gg.modl.proto.modl.v1.AdminSecurityEventsResponse;
import gg.modl.proto.modl.v1.AdminSecurityLast24Hours;
import gg.modl.proto.modl.v1.AdminSecurityLast7Days;
import gg.modl.proto.modl.v1.AdminSecurityPagination;
import gg.modl.proto.modl.v1.AdminSecuritySummaryData;
import gg.modl.proto.modl.v1.AdminSecuritySummaryResponse;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

final class AdminSecurityProtoMapper {

    private AdminSecurityProtoMapper() {
    }

    static AdminSecurityEventsResponse toEventsResponse(AdminSecurityEvents response) {
        AdminSecurityEventsData.Builder dataBuilder = AdminSecurityEventsData.newBuilder()
            .setPagination(toPagination(response.pagination()));
        response.events().forEach(event -> dataBuilder.addEvents(toEvent(event)));
        return AdminSecurityEventsResponse.newBuilder()
            .setSuccess(true)
            .setData(dataBuilder.build())
            .build();
    }

    static AdminSecuritySummaryResponse toSummaryResponse(AdminSecuritySummary response) {
        AdminSecuritySummary.Last24Hours last24Hours = response.last24Hours();
        AdminSecuritySummaryData.Builder dataBuilder = AdminSecuritySummaryData.newBuilder()
            .setLast24Hours(AdminSecurityLast24Hours.newBuilder()
                .setCritical(last24Hours.critical())
                .setHigh(last24Hours.high())
                .setMedium(last24Hours.medium())
                .build())
            .setLast7Days(AdminSecurityLast7Days.newBuilder()
                .setTotal(response.last7DaysTotal())
                .build());
        if (response.timestamp() != null) {
            dataBuilder.setTimestamp(toTimestamp(response.timestamp()));
        }
        return AdminSecuritySummaryResponse.newBuilder()
            .setSuccess(true)
            .setData(dataBuilder.build())
            .build();
    }

    private static AdminSecurityPagination toPagination(AdminPagination pagination) {
        return AdminSecurityPagination.newBuilder()
            .setPage(pagination.page())
            .setLimit(pagination.limit())
            .setTotal(pagination.total())
            .setPages(pagination.pages())
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
