package gg.modl.backend.player.controller;

import gg.modl.backend.player.external.CrafatarProxyService;
import gg.modl.backend.rest.RESTMappingV1;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_PLAYERS)
@RequiredArgsConstructor
public class PublicPlayerController {
    private final CrafatarProxyService crafatarProxyService;

    @GetMapping("/avatar/{uuid}")
    public ResponseEntity<byte[]> proxyAvatar(
            @PathVariable String uuid,
            @RequestParam(defaultValue = "32") int size,
            @RequestParam(defaultValue = "true") boolean overlay
    ) {
        byte[] avatar = crafatarProxyService.getAvatar(uuid, size, overlay);
        if (avatar == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(avatar);
    }
}
