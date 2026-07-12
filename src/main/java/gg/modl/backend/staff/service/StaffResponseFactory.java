package gg.modl.backend.staff.service;

import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.response.StaffResponse;

final class StaffResponseFactory {
    private StaffResponseFactory() {
    }

    static StaffResponse of(Staff staff, String status, String roleName) {
        return new StaffResponse(
            staff.getId(),
            staff.getEmail(),
            staff.getUsername(),
            roleName,
            status,
            staff.getAssignedMinecraftUuid(),
            staff.getAssignedMinecraftUsername(),
            staff.getCreatedAt()
        );
    }
}
