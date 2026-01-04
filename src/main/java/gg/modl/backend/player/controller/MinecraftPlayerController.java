package gg.modl.backend.player.controller;

import gg.modl.backend.player.PlayerResponseMessage;
import gg.modl.backend.player.PlayerService;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.validation.RegExpConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_PLAYERS)
@RequiredArgsConstructor
public class MinecraftPlayerController {
    private final PlayerService playerService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody @Valid LoginRequest request,
            BindingResult bindingResult,
            HttpServletRequest httpRequest
    ) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new LoginResponse(false, PlayerResponseMessage.LOGIN_INVALID_SCHEMA));
        }

        Server server = RequestUtil.getRequestServer(httpRequest);
        Player player = playerService.loginPlayer(
                server,
                UUID.fromString(request.minecraftUUID()),
                request.username(),
                request.ip()
        );

        boolean isNewPlayer = player.getUsernames().size() == 1;
        return ResponseEntity.status(isNewPlayer ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new LoginResponse(true, PlayerResponseMessage.LOGIN_SUCCESS));
    }

    public record LoginRequest(
            @Pattern(regexp = RegExpConstants.UUID) String minecraftUUID,
            @Pattern(regexp = RegExpConstants.MINECRAFT_USERNAME) String username,
            @Pattern(regexp = RegExpConstants.IP) String ip
    ) {}

    public record LoginResponse(boolean success, String message) {}
}
