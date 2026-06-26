package gg.modl.backend.alert.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;

import gg.modl.backend.alert.data.SystemAlert;
import gg.modl.backend.alert.data.SystemAlertAudience;
import gg.modl.backend.alert.data.SystemAlertSeverity;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.proto.modl.v1.AdminSystemAlertResponse;
import gg.modl.proto.modl.v1.AdminSystemAlertsResponse;
import gg.modl.proto.modl.v1.PanelSystemAlertResponse;
import gg.modl.proto.modl.v1.PanelSystemAlertsResponse;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.Nullable;

final class AlertProtoMapper {

    private AlertProtoMapper() {
    }

    static PanelSystemAlertsResponse toPanelAlerts(List<SystemAlert> alerts) {
        PanelSystemAlertsResponse.Builder builder = PanelSystemAlertsResponse.newBuilder();
        alerts.forEach(alert -> builder.addItems(toPanelAlert(alert)));
        return builder.build();
    }

    static AdminSystemAlertsResponse toAdminAlerts(List<SystemAlert> alerts) {
        AdminSystemAlertsResponse.Builder builder = AdminSystemAlertsResponse.newBuilder();
        alerts.forEach(alert -> builder.addItems(toAdminAlert(alert)));
        return builder.build();
    }

    static AdminSystemAlertResponse toAdminAlert(SystemAlert alert) {
        return AdminSystemAlertResponse.newBuilder()
            .setId(stringValue(alert.getId()))
            .setMessage(stringValue(alert.getMessage()))
            .setSeverity(severityName(alert.getSeverity()))
            .setAudience(audienceName(alert.getAudience()))
            .setExpiresAt(longValue(alert.getExpiresAt()))
            .setCreatedAt(longValue(alert.getCreatedAt()))
            .setUpdatedAt(longValue(alert.getUpdatedAt()))
            .build();
    }

    static SystemAlertSeverity parseSeverity(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SystemAlertSeverity.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid alert severity: " + value);
        }
    }

    static SystemAlertAudience parseAudience(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SystemAlertAudience.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid alert audience: " + value);
        }
    }

    static SystemAlertSeverity parseSeverityStrict(@Nullable String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Alert severity cannot be blank");
        }
        return parseSeverity(value);
    }

    static SystemAlertAudience parseAudienceStrict(@Nullable String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Alert audience cannot be blank");
        }
        return parseAudience(value);
    }

    @Nullable
    static Date toExpiresAt(long millis) {
        return millis > 0 ? new Date(millis) : null;
    }

    private static PanelSystemAlertResponse toPanelAlert(SystemAlert alert) {
        return PanelSystemAlertResponse.newBuilder()
            .setId(stringValue(alert.getId()))
            .setMessage(stringValue(alert.getMessage()))
            .setSeverity(severityName(alert.getSeverity()))
            .setAudience(audienceName(alert.getAudience()))
            .setExpiresAt(longValue(alert.getExpiresAt()))
            .build();
    }

    private static String severityName(@Nullable SystemAlertSeverity severity) {
        return severity == null ? "" : severity.name();
    }

    private static String audienceName(@Nullable SystemAlertAudience audience) {
        return audience == null ? "" : audience.name();
    }
}
