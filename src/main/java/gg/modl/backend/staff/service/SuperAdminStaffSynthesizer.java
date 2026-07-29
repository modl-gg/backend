package gg.modl.backend.staff.service;

import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import java.util.Date;
import org.jetbrains.annotations.Nullable;

public final class SuperAdminStaffSynthesizer {
    public static final String SUPER_ADMIN_USERNAME = "Admin";

    private SuperAdminStaffSynthesizer() {
    }

    public static Staff synthesizeFor(Server server, String adminEmail) {
        return Staff.builder()
            .email(EmailAddressUtil.normalize(adminEmail))
            .username(SUPER_ADMIN_USERNAME)
            .roleId(RoleAuthorization.SUPER_ADMIN_ROLE_ID)
            .createdAt(server.getCreatedAt())
            .updatedAt(new Date())
            .build();
    }

    @Nullable
    public static Staff synthesizeIfAdminEmail(Server server, String email) {
        if (!RoleAuthorization.isSuperAdminEmail(server, email)) {
            return null;
        }
        return synthesizeFor(server, server.getAdminEmail());
    }
}
