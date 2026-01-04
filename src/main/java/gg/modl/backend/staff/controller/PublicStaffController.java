package gg.modl.backend.staff.controller;

import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.dto.response.StaffResponse;
import gg.modl.backend.staff.service.InvitationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/public/staff")
@RequiredArgsConstructor
public class PublicStaffController {
    private final InvitationService invitationService;

    @GetMapping("/invitations/accept")
    public ResponseEntity<?> acceptInvitation(
            @RequestParam(required = false) String token,
            HttpServletRequest request
    ) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid invitation link."));
        }

        Server server = RequestUtil.getRequestServer(request);

        try {
            StaffResponse staff = invitationService.acceptInvitation(server, token);

            return ResponseEntity.ok(Map.of(
                    "message", "Invitation accepted successfully.",
                    "staffMember", Map.of(
                            "email", staff.email(),
                            "username", staff.username(),
                            "role", staff.role()
                    )
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Internal server error."));
        }
    }
}
