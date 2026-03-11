package gg.modl.backend.staff.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.service.StaffTwoFactorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_STAFF)
@Slf4j
@RequiredArgsConstructor
public class MinecraftStaff2faController {
    private final StaffTwoFactorService staffTwoFactorService;

    @PostMapping("/2fa/generate")
    public ResponseEntity<Map<String, Object>> generate2faToken(
        @RequestBody @Valid Generate2faRequest request,
        HttpServletRequest httpRequest) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        StaffTwoFactorService.TwoFactorTokenResult tokenResult = staffTwoFactorService
            .generateToken(server, request.minecraftUuid(), request.ip())
            .orElse(null);
        if (tokenResult == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
            "token", tokenResult.token(),
            "verifyUrl", tokenResult.verifyUrl()
        ));
    }

    public record Generate2faRequest(
        @NotBlank String minecraftUuid,
        @NotBlank String ip
    ) {}
}
