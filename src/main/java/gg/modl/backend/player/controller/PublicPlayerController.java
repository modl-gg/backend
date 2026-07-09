package gg.modl.backend.player.controller;

import gg.modl.backend.player.external.CrafatarProxyService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_PLAYERS)
@RequiredArgsConstructor
@Validated
public class PublicPlayerController {
    private final CrafatarProxyService crafatarProxyService;

    @GetMapping("/avatar/{uuid}")
    public ResponseEntity<byte[]> proxyAvatar(
        @PathVariable String uuid,
        @RequestParam(defaultValue = "32") int size,
        @RequestParam(defaultValue = "true") boolean overlay
    ) {
        String normalized = normalizeUuid(uuid);
        if (normalized == null) {
            return ResponseEntity.badRequest().build();
        }

        byte[] avatar = crafatarProxyService.getAvatar(normalized, size, overlay);
        if (avatar == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(avatar);
    }

    private static String normalizeUuid(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.matches("^[0-9a-fA-F]{32}$")) {
            s = s.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
        }
        try {
            return UUID.fromString(s).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
