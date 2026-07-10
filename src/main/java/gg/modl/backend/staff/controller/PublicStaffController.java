package gg.modl.backend.staff.controller;

import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.dto.response.StaffResponse;
import gg.modl.backend.staff.service.InvitationService;
import gg.modl.backend.staff.service.StaffTwoFactorService;
import gg.modl.proto.modl.v1.AcceptInvitationResponse;
import gg.modl.proto.modl.v1.AcceptInvitationResponse.StaffInviteAcceptedMember;
import gg.modl.proto.modl.v1.Staff2faVerifyResponse;
import gg.modl.proto.modl.v1.SyncStaff2faVerification;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/public/staff")
@RequiredArgsConstructor
public class PublicStaffController {
    private final InvitationService invitationService;
    private final StaffTwoFactorService staffTwoFactorService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @GetMapping("/invitations/accept")
    public ResponseEntity<AcceptInvitationResponse> acceptInvitationGet(
        @RequestParam(required = false) String token,
        HttpServletRequest request
    ) {
        return acceptInvitationInternal(token, request);
    }

    private ResponseEntity<AcceptInvitationResponse> acceptInvitationInternal(String token, HttpServletRequest request) {
        if (token == null || token.isBlank()) {
            throw new ValidationException("Invalid invitation link.");
        }

        Server server = RequestUtil.getRequestServer(request);
        StaffResponse staff = invitationService.acceptInvitation(server, token);

        return ResponseEntity.ok(AcceptInvitationResponse.newBuilder()
            .setMessage("Invitation accepted successfully.")
            .setStaffMember(StaffInviteAcceptedMember.newBuilder()
                .setEmail(nullToEmpty(staff.email()))
                .setUsername(nullToEmpty(staff.username()))
                .setRole(nullToEmpty(staff.role()))
                .build())
            .build());
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<AcceptInvitationResponse> acceptInvitationPost(
        @RequestParam(required = false) String token,
        @RequestBody(required = false) gg.modl.proto.modl.v1.AcceptInvitationRequest body,
        HttpServletRequest request
    ) {
        String resolvedToken = token;
        if ((resolvedToken == null || resolvedToken.isBlank()) && body != null) {
            resolvedToken = body.getToken();
        }

        return acceptInvitationInternal(resolvedToken, request);
    }

    @PostMapping("/2fa/verify/{token}")
    public ResponseEntity<Staff2faVerifyResponse> verify2faToken(
        @PathVariable String token,
        HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        String sessionEmail = RequestUtil.getSessionEmail(request);

        Optional<StaffTwoFactorService.VerificationResult> verification =
            staffTwoFactorService.verifyToken(server, token, sessionEmail);
        if (verification.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        verification.get().minecraftUuidOptional().ifPresent(minecraftUuid ->
            realtimeEventPublisher.pushStaff2fa(server, List.of(SyncStaff2faVerification.newBuilder()
                .setMinecraftUuid(minecraftUuid)
                .build()), token));

        return ResponseEntity.ok(Staff2faVerifyResponse.newBuilder().setStatus("verified").build());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
