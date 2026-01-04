package gg.modl.backend.staff.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.dto.request.*;
import gg.modl.backend.staff.dto.response.AvailablePlayerResponse;
import gg.modl.backend.staff.dto.response.InviteResultResponse;
import gg.modl.backend.staff.dto.response.StaffResponse;
import gg.modl.backend.staff.service.InvitationService;
import gg.modl.backend.staff.service.StaffService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_STAFF)
@RequiredArgsConstructor
public class PanelStaffController {
    private final StaffService staffService;
    private final InvitationService invitationService;

    @GetMapping
    public ResponseEntity<List<StaffResponse>> getAllStaff(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<StaffResponse> staff = staffService.getAllStaff(server);
        return ResponseEntity.ok(staff);
    }

    @GetMapping("/check-username/{username}")
    public ResponseEntity<Map<String, Boolean>> checkUsername(
            @PathVariable String username,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        boolean exists = staffService.checkUsernameExists(server, username);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    @GetMapping("/{username}")
    public ResponseEntity<StaffResponse> getStaffByUsername(
            @PathVariable String username,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return staffService.getStaffByUsername(server, username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createStaff(
            @RequestBody @Valid CreateStaffRequest createRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            StaffResponse staff = staffService.createStaff(server, createRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(staff);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{username}")
    public ResponseEntity<?> updateStaff(
            @PathVariable String username,
            @RequestBody @Valid UpdateStaffRequest updateRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        // TODO: Get current user email from session
        String currentUserEmail = ""; // Placeholder

        try {
            return staffService.updateStaff(server, username, updateRequest, currentUserEmail)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<?> updateStaffRole(
            @PathVariable String id,
            @RequestBody @Valid UpdateStaffRoleRequest roleRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        // TODO: Get current user from session
        String performerEmail = "";
        String performerRole = "";

        try {
            return staffService.updateStaffRole(server, id, roleRequest.role(), performerEmail, performerRole)
                    .map(staff -> ResponseEntity.ok(Map.of(
                            "message", "Role updated successfully.",
                            "staffMember", staff
                    )))
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStaff(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        // TODO: Get current user from session
        String removerEmail = "";
        String removerRole = "";

        try {
            boolean deleted = staffService.deleteStaff(server, id, removerEmail, removerRole);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "Removed successfully."));
            }
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/invite")
    public ResponseEntity<?> inviteStaff(
            @RequestBody @Valid InviteStaffRequest inviteRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        // TODO: Get current user email from session
        String inviterEmail = "";

        try {
            InviteResultResponse result = invitationService.sendInvitations(server, inviteRequest, inviterEmail);

            if (result.success().isEmpty()) {
                return ResponseEntity.badRequest().body(result);
            } else if (result.failed().isEmpty()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", result.message()));
            } else {
                return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(result);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/invitations/{id}/resend")
    public ResponseEntity<?> resendInvitation(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        boolean resent = invitationService.resendInvitation(server, id);
        if (resent) {
            return ResponseEntity.ok(Map.of("message", "Invitation resent successfully"));
        }
        return ResponseEntity.notFound().build();
    }

    @PatchMapping("/{username}/minecraft-player")
    public ResponseEntity<?> assignMinecraftPlayer(
            @PathVariable String username,
            @RequestBody AssignMinecraftPlayerRequest assignRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            return staffService.assignMinecraftPlayer(server, username, assignRequest)
                    .map(staff -> ResponseEntity.ok(Map.of(
                            "message", (assignRequest.minecraftUuid() == null && assignRequest.minecraftUsername() == null)
                                    ? "Minecraft player assignment cleared successfully"
                                    : "Minecraft player assigned successfully",
                            "staffMember", staff
                    )))
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/available-players")
    public ResponseEntity<Map<String, List<AvailablePlayerResponse>>> getAvailablePlayers(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<AvailablePlayerResponse> players = staffService.getAvailablePlayers(server);
        return ResponseEntity.ok(Map.of("players", players));
    }
}
