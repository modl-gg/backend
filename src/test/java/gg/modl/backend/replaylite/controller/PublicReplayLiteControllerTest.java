package gg.modl.backend.replaylite.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import gg.modl.backend.replaylite.service.ReplayLiteService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PublicReplayLiteControllerTest {
    @Test
    void downloadReplayStreamsBytesWithoutRedirectOrPublicCache() {
        ReplayLiteService service = Mockito.mock(ReplayLiteService.class);
        PublicReplayLiteController controller = new PublicReplayLiteController(service);
        byte[] replayBytes = new byte[] {1, 2, 3};
        when(service.getPublicReplayDownload("replay-1"))
            .thenReturn(Optional.of(new ReplayLiteService.ReplayLiteDownload(
                replayBytes,
                "application/octet-stream",
                Instant.parse("2026-04-25T12:00:00Z")
            )));

        ResponseEntity<?> response = controller.downloadReplay("replay-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(replayBytes, (byte[]) response.getBody());
        assertNull(response.getHeaders().getLocation());
        assertEquals("private, no-store, max-age=0", response.getHeaders().getCacheControl());
        assertEquals("no-cache", response.getHeaders().getPragma());
    }
}
