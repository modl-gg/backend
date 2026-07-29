package gg.modl.backend.admin.dto.response;

import java.util.Date;
import java.util.List;

public record AdminMonitoringHealth(
    String status,
    List<HealthCheck> checks,
    Date timestamp
) {
    public record HealthCheck(
        String name,
        String status,
        String message,
        Long responseTime,
        String error,
        Long count
    ) {
        public static HealthCheck responsive(String name, String status, String message, long responseTime) {
            return new HealthCheck(name, status, message, responseTime, null, null);
        }

        public static HealthCheck failure(String name, String status, String message, String error) {
            return new HealthCheck(name, status, message, null, error, null);
        }

        public static HealthCheck counted(String name, String status, String message, long count) {
            return new HealthCheck(name, status, message, null, null, count);
        }
    }
}
