package gg.modl.backend.admin.dto.response;

public record AdminMaintenanceStatus(
    boolean active,
    String message
) {
}
