package gg.modl.backend.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.replay.dto.PlayerReplayResponse;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageMetadataService;
import gg.modl.backend.storage.service.StorageQuotaService;
import gg.modl.backend.ticket.data.Ticket;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReplayServiceTest {

    @Mock
    private ReplayMongoRepository replayRepository;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private StorageQuotaService storageQuotaService;

    @Mock
    private TrainingDataService trainingDataService;

    @Mock
    private StorageMetadataService storageMetadataService;

    @Mock
    private TicketMongoRepository ticketRepository;

    private ReplayService replayService;
    private Server server;
    private static Validator validator;

    @BeforeEach
    void setUp() {
        if (validator == null) {
            validator = Validation.buildDefaultValidatorFactory().getValidator();
        }
        replayService = new ReplayService(
            replayRepository,
            s3StorageService,
            storageQuotaService,
            trainingDataService,
            storageMetadataService,
            ticketRepository,
            validator
        );
        ReflectionTestUtils.setField(replayService, "maxFileSize", 10 * 1024 * 1024L);
        server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
    }

    @Test
    void initUploadRejectsFilesThatExceedStorageQuotaWithValidationException() {
        when(storageQuotaService.canUpload(server, 1024L)).thenReturn(false);

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> replayService.initUpload(server, "1.21.4", 1024L, null, null)
        );

        assertEquals("Storage quota exceeded", exception.getMessage());
        verifyNoInteractions(s3StorageService, replayRepository, trainingDataService, storageMetadataService);
    }

    @Test
    void initUploadRejectsFilesThatExceedConfiguredMaxSizeWithValidationException() {
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> replayService.initUpload(server, "1.21.4", (10 * 1024 * 1024L) + 1, null, null)
        );

        assertEquals("File size exceeds maximum of 10 MB", exception.getMessage());
        verifyNoInteractions(storageQuotaService, s3StorageService, replayRepository, trainingDataService, storageMetadataService);
    }

    @Test
    void initUploadPersistsTargetMetadata() {
        when(storageQuotaService.canUpload(server, 2048L)).thenReturn(true);
        when(s3StorageService.createPresignedUploadUrl(server, "replays", "replay.modlreplay", "application/octet-stream", 2048L))
            .thenReturn(new PresignUploadResponse(
                "https://upload.example/replay",
                "db/replays/replay.modlreplay",
                Instant.parse("2026-05-18T12:00:00Z"),
                "PUT",
                Map.of("Content-Type", "application/octet-stream")
            ));

        replayService.initUpload(server, "1.21.4", 2048L, " 3f8c9c5a-6b6e-4f2c-9b7f-1a2b3c4d5e6f ", " TargetName ");

        ArgumentCaptor<ReplayDocument> replayCaptor = ArgumentCaptor.forClass(ReplayDocument.class);
        verify(replayRepository).saveEntity(eq(server), replayCaptor.capture());
        ReplayDocument saved = replayCaptor.getValue();
        assertEquals("3f8c9c5a-6b6e-4f2c-9b7f-1a2b3c4d5e6f", saved.getTargetUuid());
        assertEquals("TargetName", saved.getTargetName());
        assertEquals(ReplayDocument.STATUS_PENDING, saved.getStatus());
    }

    @Test
    void initUploadRejectsInvalidTargetUuid() {
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> replayService.initUpload(server, "1.21.4", 2048L, "not-a-uuid", "TargetName")
        );

        assertEquals("Target UUID must be a valid UUID", exception.getMessage());
        verifyNoInteractions(s3StorageService, replayRepository, trainingDataService, storageMetadataService);
    }

    @Test
    void initUploadRejectsLongTargetName() {
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> replayService.initUpload(server, "1.21.4", 2048L, "3f8c9c5a-6b6e-4f2c-9b7f-1a2b3c4d5e6f", "x".repeat(17))
        );

        assertEquals("Target name is too long", exception.getMessage());
        verifyNoInteractions(s3StorageService, replayRepository, trainingDataService, storageMetadataService);
    }

    @Test
    void listPlayerReplaysCombinesDirectMetadataAndTicketFallbackWithoutDuplicateUrls() {
        String requestedPlayerUuid = "3F8C9C5A-6B6E-4F2C-9B7F-1A2B3C4D5E6F";
        String normalizedPlayerUuid = "3f8c9c5a-6b6e-4f2c-9b7f-1a2b3c4d5e6f";

        ReplayDocument directReplay = new ReplayDocument();
        directReplay.setId("replay-1");
        directReplay.setTargetUuid(normalizedPlayerUuid);
        directReplay.setTargetName("Player");
        directReplay.setMcVersion("1.21.4");
        directReplay.setFileSize(4096L);
        directReplay.setCreatedAt(new Date(1000L));
        directReplay.setStatus(ReplayDocument.STATUS_COMPLETE);
        directReplay.setStorageKey("db/replays/replay-1.modlreplay");

        Ticket duplicateUrlTicket = Ticket.builder()
            .id("ticket-1")
            .creatorUuid(normalizedPlayerUuid)
            .creatorName("Player")
            .created(new Date(2000L))
            .replayUrl("https://cdn.example/db/replays/replay-1.modlreplay")
            .build();
        Ticket duplicateRawIdTicket = Ticket.builder()
            .id("ticket-raw-duplicate")
            .creatorUuid(normalizedPlayerUuid)
            .creatorName("Player")
            .created(new Date(2500L))
            .replayUrl("replay-1")
            .build();
        Ticket duplicateQueryIdTicket = Ticket.builder()
            .id("ticket-query-duplicate")
            .creatorUuid(normalizedPlayerUuid)
            .creatorName("Player")
            .created(new Date(2750L))
            .replayUrl("https://replays.example/?id=replay-1")
            .build();
        Ticket fallbackTicket = Ticket.builder()
            .id("ticket-2")
            .reportedPlayerUuid(normalizedPlayerUuid)
            .reportedPlayer("Player")
            .created(new Date(3000L))
            .replayUrl("replay-2")
            .build();

        when(replayRepository.findByTargetUuid(server, normalizedPlayerUuid, 100)).thenReturn(List.of(directReplay));
        when(s3StorageService.getCdnUrl("db/replays/replay-1.modlreplay"))
            .thenReturn("https://cdn.example/db/replays/replay-1.modlreplay");
        when(ticketRepository.findPlayerTicketsWithReplayUrl(server, normalizedPlayerUuid, 100))
            .thenReturn(List.of(duplicateUrlTicket, duplicateRawIdTicket, duplicateQueryIdTicket, fallbackTicket));

        List<PlayerReplayResponse> replays = replayService.listPlayerReplays(server, requestedPlayerUuid);

        assertEquals(2, replays.size());
        assertEquals(PlayerReplayResponse.MatchSource.DIRECT_METADATA, replays.get(0).matchSource());
        assertEquals("replay-1", replays.get(0).replayId());
        assertEquals(PlayerReplayResponse.MatchSource.TICKET_FALLBACK, replays.get(1).matchSource());
        assertEquals("replay-2", replays.get(1).replayId());
        assertEquals("replay-2", replays.get(1).replayUrl());
    }

    @Test
    void listPlayerReplaysHidesCompleteReplayWhoseFileNoLongerExists() {
        String playerUuid = "3f8c9c5a-6b6e-4f2c-9b7f-1a2b3c4d5e6f";
        ReplayDocument orphaned = completeReplay("replay-gone", playerUuid, "db/replays/replay-gone.modlreplay");

        when(storageMetadataService.isMetadataAuthoritative(server)).thenReturn(true);
        when(replayRepository.findByTargetUuid(server, playerUuid, 100)).thenReturn(List.of(orphaned));
        when(storageMetadataService.existingKeys(eq(server), any())).thenReturn(Set.of());
        when(ticketRepository.findPlayerTicketsWithReplayUrl(server, playerUuid, 100)).thenReturn(List.of());

        List<PlayerReplayResponse> replays = replayService.listPlayerReplays(server, playerUuid);

        assertEquals(0, replays.size());
    }

    @Test
    void listPlayerReplaysKeepsCompleteReplayWhenStorageMetadataIsNotAuthoritative() {
        String playerUuid = "3f8c9c5a-6b6e-4f2c-9b7f-1a2b3c4d5e6f";
        ReplayDocument replay = completeReplay("replay-1", playerUuid, "db/replays/replay-1.modlreplay");

        when(storageMetadataService.isMetadataAuthoritative(server)).thenReturn(false);
        when(replayRepository.findByTargetUuid(server, playerUuid, 100)).thenReturn(List.of(replay));
        when(s3StorageService.getCdnUrl("db/replays/replay-1.modlreplay")).thenReturn("https://cdn.example/replay-1");
        when(ticketRepository.findPlayerTicketsWithReplayUrl(server, playerUuid, 100)).thenReturn(List.of());

        List<PlayerReplayResponse> replays = replayService.listPlayerReplays(server, playerUuid);

        assertEquals(1, replays.size());
        assertEquals("replay-1", replays.get(0).replayId());
        verify(storageMetadataService, never()).existingKeys(any(), any());
    }

    @Test
    void listPlayerReplaysSkipsTicketFallbackWhenReferencedReplayFileIsGone() {
        String playerUuid = "3f8c9c5a-6b6e-4f2c-9b7f-1a2b3c4d5e6f";
        ReplayDocument orphaned = completeReplay("replay-1", playerUuid, "db/replays/replay-1.modlreplay");
        Ticket ticket = Ticket.builder()
            .id("ticket-1")
            .creatorUuid(playerUuid)
            .creatorName("Player")
            .created(new Date(2000L))
            .replayUrl("https://replays.example/?id=replay-1")
            .build();

        when(storageMetadataService.isMetadataAuthoritative(server)).thenReturn(true);
        when(replayRepository.findByTargetUuid(server, playerUuid, 100)).thenReturn(List.of(orphaned));
        when(storageMetadataService.existingKeys(eq(server), any())).thenReturn(Set.of());
        when(ticketRepository.findPlayerTicketsWithReplayUrl(server, playerUuid, 100)).thenReturn(List.of(ticket));

        List<PlayerReplayResponse> replays = replayService.listPlayerReplays(server, playerUuid);

        assertEquals(0, replays.size());
    }

    private ReplayDocument completeReplay(String id, String targetUuid, String storageKey) {
        ReplayDocument replay = new ReplayDocument();
        replay.setId(id);
        replay.setTargetUuid(targetUuid);
        replay.setTargetName("Player");
        replay.setMcVersion("1.21.4");
        replay.setFileSize(4096L);
        replay.setCreatedAt(new Date(1000L));
        replay.setStatus(ReplayDocument.STATUS_COMPLETE);
        replay.setStorageKey(storageKey);
        return replay;
    }
}
