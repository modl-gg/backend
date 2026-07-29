package gg.modl.backend.staff.service;

import gg.modl.backend.auth.WebAuthnService;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.database.mongo.repository.InvitationMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.service.ServerTimestampService;
import gg.modl.backend.settings.service.GeneralSettingsService;
import gg.modl.backend.staff.data.Invitation;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.request.CreateStaffRequest;
import gg.modl.backend.staff.dto.request.UpdateStaffRequest;
import gg.modl.backend.staff.dto.response.StaffResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaffService {
    private final InvitationMongoRepository invitationRepository;
    private final StaffMongoRepository staffRepository;
    private final PermissionService permissionService;
    private final RoleAuthorization roleAuthorization;
    private final ServerTimestampService serverTimestampService;
    private final WebAuthnService webAuthnService;
    private final SessionService sessionService;
    private final GeneralSettingsService generalSettingsService;
    private final StaffLookupCache staffLookupCache;

    public List<StaffResponse> getAllStaff(Server server) {
        List<Staff> staffMembers = staffRepository.findAll(server);
        List<Invitation> pendingInvitations = invitationRepository.findActiveInvitations(server, new Date());

        Set<String> roleIds = new HashSet<>();
        staffMembers.forEach(staff -> roleIds.add(staff.getRoleId()));
        pendingInvitations.forEach(invitation -> roleIds.add(invitation.getRoleId()));
        roleIds.add(RoleAuthorization.SUPER_ADMIN_ROLE_ID);
        Map<String, String> roleNamesById = permissionService.resolveRoleNames(server, roleIds);

        List<StaffResponse> result = new ArrayList<>();

        String adminEmail = server.getAdminEmail();
        boolean superAdminFound = false;

        for (Staff staff : staffMembers) {
            result.add(StaffResponseFactory.of(staff, "Active", fallbackRoleName(roleNamesById, staff.getRoleId())));
            if (adminEmail != null && adminEmail.equalsIgnoreCase(staff.getEmail())) {
                superAdminFound = true;
            }
        }

        if (!superAdminFound && adminEmail != null) {
            result.add(0, StaffResponseFactory.of(SuperAdminStaffSynthesizer.synthesizeFor(server, adminEmail), "Active",
                fallbackRoleName(roleNamesById, RoleAuthorization.SUPER_ADMIN_ROLE_ID)));
        }

        for (Invitation invitation : pendingInvitations) {
            result.add(new StaffResponse(
                invitation.getId(),
                invitation.getEmail(),
                null,
                fallbackRoleName(roleNamesById, invitation.getRoleId()),
                "Pending Invitation",
                null,
                null,
                invitation.getCreatedAt()
            ));
        }

        return result;
    }

    public long countStaffIncludingSuperAdmin(Server server) {
        long staffCount = staffRepository.countAll(server);
        String adminEmail = server.getAdminEmail();
        if (adminEmail != null && !staffRepository.existsByEmailEqualsIgnoreCase(server, adminEmail)) {
            staffCount++;
        }
        return staffCount;
    }

    private StaffResponse toStaffResponse(Server server, Staff staff, String status) {
        return StaffResponseFactory.of(staff, status, permissionService.resolveRoleName(server, staff.getRoleId()));
    }

    private static String fallbackRoleName(Map<String, String> roleNamesById, String roleId) {
        if (roleId == null) {
            return "";
        }
        return roleNamesById.getOrDefault(roleId, roleId);
    }

    public Optional<StaffResponse> getStaffByUsername(Server server, String username) {
        return staffRepository.findByUsername(server, username).map(s -> toStaffResponse(server, s, "Active"));
    }

    public boolean checkUsernameExists(Server server, String username) {
        return staffRepository.existsByUsername(server, username);
    }

    public StaffResponse createStaff(Server server, CreateStaffRequest request, RoleAuthorization.PerformerAuthority performer) {
        String email = EmailAddressUtil.normalizeIfValid(request.email());
        if (email == null) {
            throw new ValidationException("A valid email address is required");
        }

        if (staffRepository.existsByEmailOrUsername(server, email, request.username())) {
            throw new ConflictException("Staff member with this email or username already exists");
        }

        String requestedRole = request.role() != null ? request.role() : "Helper";
        StaffRole grantedRole = roleAuthorization.assertGrantableRole(server, performer, requestedRole);

        Staff staff = Staff.builder()
            .email(email)
            .username(request.username())
            .roleId(grantedRole.getId())
            .language(generalSettingsService.getGeneralSettings(server).getDefaultLanguage())
            .createdAt(new Date())
            .updatedAt(new Date())
            .build();

        staffRepository.saveEntity(server, staff);
        staffLookupCache.evict(server, staff.getEmail());
        serverTimestampService.updateStaffPermissionsTimestamp(server);

        return toStaffResponse(server, staff, "Active");
    }

    public Optional<StaffResponse> updateStaff(Server server, String username, UpdateStaffRequest request) {
        Staff staff = staffRepository.findByUsername(server, username).orElse(null);

        if (staff == null) {
            return Optional.empty();
        }

        if (request.email() != null) {
            String requestedEmail = EmailAddressUtil.normalizeIfValid(request.email());
            if (requestedEmail == null) {
                throw new ValidationException("A valid email address is required");
            }
            if (!requestedEmail.equals(staff.getEmail())) {
                throw new ForbiddenException("Email changes must be confirmed through email verification in Account Settings");
            }
        }

        return Optional.of(toStaffResponse(server, staff, "Active"));
    }

    public boolean deleteStaff(Server server, String id, RoleAuthorization.PerformerAuthority performer) {
        if (invitationRepository.deleteById(server, id)) {
            return true;
        }

        Staff staffToRemove = staffRepository.findById(server, id).orElse(null);

        if (staffToRemove == null) {
            return false;
        }

        roleAuthorization.assertCanActOnStaff(server, performer, staffToRemove);

        staffRepository.deleteById(server, id);
        staffLookupCache.evict(server, staffToRemove.getEmail());
        webAuthnService.deleteCredentialsForEmail(server, staffToRemove.getEmail());
        serverTimestampService.updateStaffPermissionsTimestamp(server);
        return true;
    }

    public Optional<StaffResponse> updateStaffRole(Server server, String id, String newRole, RoleAuthorization.PerformerAuthority performer) {
        Staff staff = staffRepository.findById(server, id).orElse(null);

        if (staff == null) {
            return Optional.empty();
        }

        roleAuthorization.assertCanActOnStaff(server, performer, staff);

        StaffRole validatedRole = roleAuthorization.assertGrantableRole(server, performer, newRole);
        staff.setRoleId(validatedRole.getId());
        staff.setUpdatedAt(new Date());
        Staff saved = staffRepository.saveEntity(server, staff);
        staffLookupCache.evict(server, staff.getEmail());
        serverTimestampService.updateStaffPermissionsTimestamp(server);

        return Optional.of(toStaffResponse(server, saved, "Active"));
    }

    public void offboardPreviousAdminEmail(Server server, String email) {
        if (email == null) {
            return;
        }
        staffRepository.findByEmailIgnoreCase(server, email)
            .filter(staff -> RoleAuthorization.SUPER_ADMIN_ROLE_ID.equals(staff.getRoleId()))
            .ifPresent(staff -> {
                staffRepository.deleteById(server, staff.getId());
                serverTimestampService.updateStaffPermissionsTimestamp(server);
            });
        staffLookupCache.evict(server, email);
        webAuthnService.deleteCredentialsForEmail(server, email);
        sessionService.invalidateAllSessionsForEmail(server, email);
    }
}
