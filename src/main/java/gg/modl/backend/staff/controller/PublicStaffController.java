package gg.modl.backend.staff.controller;

import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.dto.request.AcceptInvitationRequest;
import gg.modl.backend.staff.dto.response.StaffResponse;
import gg.modl.backend.staff.service.InvitationService;
import gg.modl.backend.staff.service.StaffTwoFactorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
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

    @GetMapping("/invitations/accept")
    public ResponseEntity<?> acceptInvitationGet(
        @RequestParam(required = false) String token,
        HttpServletRequest request
    ) {
        return acceptInvitationInternal(token, request);
    }

    private ResponseEntity<?> acceptInvitationInternal(String token, HttpServletRequest request) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Invalid invitation link."));
        }

        Server server = RequestUtil.getRequestServer(request);

        StaffResponse staff = invitationService.acceptInvitation(server, token);

        return ResponseEntity.ok(Map.of(
            "message", "Invitation accepted successfully.",
            "staffMember", Map.of(
                "email", staff.email(),
                "username", staff.username(),
                "role", staff.role()
            )
        ));
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<?> acceptInvitationPost(
        @RequestParam(required = false) String token,
        @RequestBody(required = false) @Valid AcceptInvitationRequest body,
        HttpServletRequest request
    ) {
        String resolvedToken = token;
        if ((resolvedToken == null || resolvedToken.isBlank()) && body != null) {
            resolvedToken = body.token();
        }

        return acceptInvitationInternal(resolvedToken, request);
    }

    @PostMapping("/2fa/verify/{token}")
    public ResponseEntity<Map<String, Object>> verify2faToken(
        @PathVariable String token,
        HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        if (!staffTwoFactorService.verifyToken(server, token)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of("status", "verified"));
    }
}
