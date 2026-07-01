package gg.modl.backend.staff.controller;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.log.service.PanelActionAuditor;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.dto.request.AssignMinecraftPlayerRequest;
import gg.modl.backend.staff.dto.request.CreateStaffRequest;
import gg.modl.backend.staff.dto.request.InviteStaffRequest;
import gg.modl.backend.staff.dto.request.UpdateStaffRequest;
import gg.modl.backend.staff.dto.response.InviteResultResponse;
import gg.modl.backend.staff.dto.response.StaffResponse;
import gg.modl.backend.staff.service.InvitationService;
import gg.modl.backend.staff.service.StaffService;
import gg.modl.proto.modl.v1.AvailablePlayersResponse;
import gg.modl.proto.modl.v1.CheckUsernameResponse;
import gg.modl.proto.modl.v1.PanelResource;
import gg.modl.proto.modl.v1.PanelStaffListResponse;
import gg.modl.proto.modl.v1.StaffMutationResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_STAFF)
@RequiredArgsConstructor
public class PanelStaffController {
    private final StaffService staffService;
    private final InvitationService invitationService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final PanelActionAuditor panelActionAuditor;

    @GetMapping
    public ResponseEntity<PanelStaffListResponse> getAllStaff(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(PanelStaffProtoMapper.toStaffListResponse(staffService.getAllStaff(server)));
    }

    @GetMapping("/check-username/{username}")
    public ResponseEntity<CheckUsernameResponse> checkUsername(
        @PathVariable String username,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        boolean exists = staffService.checkUsernameExists(server, username);
        return ResponseEntity.ok(PanelStaffProtoMapper.toCheckUsernameResponse(exists));
    }

    @GetMapping("/{username}")
    public ResponseEntity<gg.modl.proto.modl.v1.StaffResponse> getStaffByUsername(
        @PathVariable String username,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return staffService.getStaffByUsername(server, username)
            .map(PanelStaffProtoMapper::toStaffResponse)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<gg.modl.proto.modl.v1.StaffResponse> createStaff(
        @RequestBody gg.modl.proto.modl.v1.CreateStaffRequest createRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String performerEmail = RequestUtil.getSessionEmail(request);
        String performerRole = resolvePerformerRole(server, performerEmail);

        CreateStaffRequest mappedRequest = PanelStaffProtoMapper.toCreateStaffRequest(createRequest);
        StaffResponse staff = staffService.createStaff(server, mappedRequest, performerEmail, performerRole);
        invalidateStaff(server);
        return ResponseEntity.status(HttpStatus.CREATED).body(PanelStaffProtoMapper.toStaffResponse(staff));
    }

    @PatchMapping("/{username}")
    public ResponseEntity<gg.modl.proto.modl.v1.StaffResponse> updateStaff(
        @PathVariable String username,
        @RequestBody gg.modl.proto.modl.v1.UpdateStaffRequest updateRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String currentUserEmail = RequestUtil.getSessionEmail(request);

        UpdateStaffRequest mappedRequest = PanelStaffProtoMapper.toUpdateStaffRequest(updateRequest);
        return staffService.updateStaff(server, username, mappedRequest, currentUserEmail)
            .map(staff -> {
                invalidateStaff(server);
                return ResponseEntity.ok(PanelStaffProtoMapper.toStaffResponse(staff));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<StaffMutationResponse> updateStaffRole(
        @PathVariable String id,
        @RequestBody gg.modl.proto.modl.v1.UpdateStaffRoleRequest roleRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String performerEmail = RequestUtil.getSessionEmail(request);
        String performerRole = resolvePerformerRole(server, performerEmail);

        return staffService.updateStaffRole(server, id, roleRequest.getRole(), performerEmail, performerRole)
            .map(staff -> {
                invalidateStaff(server);
                return ResponseEntity.ok(
                    PanelStaffProtoMapper.toStaffMutationResponse("Role updated successfully.", staff));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<StaffMutationResponse> deleteStaff(
        @PathVariable String id,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String removerEmail = RequestUtil.getSessionEmail(request);

        boolean deleted = staffService.deleteStaff(server, id, removerEmail);
        if (deleted) {
            invalidateStaff(server);
            return ResponseEntity.ok(
                PanelStaffProtoMapper.toStaffMutationResponse("Removed successfully.", null));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/invite")
    public ResponseEntity<gg.modl.proto.modl.v1.InviteResultResponse> inviteStaff(
        @RequestBody gg.modl.proto.modl.v1.InviteStaffRequest inviteRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String inviterEmail = RequestUtil.getSessionEmail(request);
        String inviterRole = resolvePerformerRole(server, inviterEmail);

        InviteStaffRequest mappedRequest = PanelStaffProtoMapper.toInviteStaffRequest(inviteRequest);
        InviteResultResponse result = invitationService.sendInvitations(server, mappedRequest, inviterEmail, inviterRole);

        if (result.success().isEmpty()) {
            return ResponseEntity.badRequest().body(PanelStaffProtoMapper.toInviteResultResponse(result));
        }

        invalidateStaff(server);
        panelActionAuditor.recordStaffAction(server, inviterEmail, "Invited staff: " + String.join(", ", result.success()));
        if (result.failed().isEmpty()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(gg.modl.proto.modl.v1.InviteResultResponse.newBuilder()
                    .setMessage(result.message())
                    .build());
        }
        return ResponseEntity.status(HttpStatus.MULTI_STATUS)
            .body(PanelStaffProtoMapper.toInviteResultResponse(result));
    }

    @PostMapping("/invitations/{id}/resend")
    public ResponseEntity<StaffMutationResponse> resendInvitation(
        @PathVariable String id,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        boolean resent = invitationService.resendInvitation(server, id);
        if (resent) {
            invalidateStaff(server);
            return ResponseEntity.ok(
                PanelStaffProtoMapper.toStaffMutationResponse("Invitation resent successfully", null));
        }
        return ResponseEntity.notFound().build();
    }

    @PatchMapping("/{username}/minecraft-player")
    public ResponseEntity<StaffMutationResponse> assignMinecraftPlayer(
        @PathVariable String username,
        @RequestBody gg.modl.proto.modl.v1.AssignMinecraftPlayerRequest assignRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        boolean clearing = !assignRequest.hasMinecraftUuid() && !assignRequest.hasMinecraftUsername();
        AssignMinecraftPlayerRequest mappedRequest = PanelStaffProtoMapper.toAssignMinecraftPlayerRequest(assignRequest);
        return staffService.assignMinecraftPlayer(server, username, mappedRequest)
            .map(staff -> {
                invalidateStaff(server);
                String message = clearing
                    ? "Minecraft player assignment cleared successfully"
                    : "Minecraft player assigned successfully";
                return ResponseEntity.ok(PanelStaffProtoMapper.toStaffMutationResponse(message, staff));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/available-players")
    public ResponseEntity<AvailablePlayersResponse> getAvailablePlayers(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(
            PanelStaffProtoMapper.toAvailablePlayersResponse(staffService.getAvailablePlayers(server)));
    }

    private void invalidateStaff(Server server) {
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_STAFF);
    }

    private String resolvePerformerRole(Server server, String email) {
        if (email != null && server.getAdminEmail() != null && email.equalsIgnoreCase(server.getAdminEmail())) {
            return "super-admin";
        }
        return staffService.getStaffByEmail(server, email)
            .map(staff -> staff.getRoleId())
            .orElse("");
    }
}
