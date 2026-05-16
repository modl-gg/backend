package gg.modl.backend.replaylite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.replaylite.data.ReplayLiteDocument;
import gg.modl.backend.replaylite.data.ReplayLiteLabel;
import gg.modl.backend.replaylite.data.ReplayLiteStatus;
import gg.modl.backend.replaylite.dto.ReplayLitePublicResponse;
import gg.modl.backend.replaylite.dto.ReplayLiteUploadInitRequest;
import gg.modl.backend.replaylite.repository.ReplayLiteMongoRepository;
import gg.modl.backend.replaylite.repository.ReplayLiteQuotaMongoRepository;
import gg.modl.backend.replaylite.storage.ReplayLiteStorageService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplayLiteServiceTest {
    private static final long MAX_SIZE = 10 * 1024 * 1024L;
    private static final Instant NOW = Instant.parse("2026-04-24T12:00:00Z");
    private static final String SERVER_ID = "507f1f77bcf86cd799439011";
    private static final UUID SERVER_UUID = UUID.nameUUIDFromBytes(SERVER_ID.getBytes(StandardCharsets.UTF_8));

    @Mock
    private ReplayLiteMongoRepository repository;

    @Mock
    private ReplayLiteStorageService storageService;

    @Mock
    private ReplayLiteAbuseGuard abuseGuard;

    @Mock
    private ReplayLiteQuotaMongoRepository quotaRepository;

    private ReplayLiteService service;

    @BeforeEach
    void setUp() {
        service = new ReplayLiteService(
            repository,
            quotaRepository,
            storageService,
            abuseGuard,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void initUploadRejectsRequestedSizeAboveTenMegabytes() {
        ReplayLiteUploadInitRequest request = new ReplayLiteUploadInitRequest(
            MAX_SIZE + 1,
            "1.21.4"
        );

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> service.initUpload(authenticatedServer(), request, "203.0.113.10")
        );

        assertEquals("Replay Lite uploads cannot exceed 10 MB", exception.getMessage());
        verifyNoInteractions(repository, storageService, abuseGuard);
    }

    @Test
    void initUploadSavesPendingMetadataForAuthenticatedServerBeforePresigning() {
        ReplayLiteUploadInitRequest request = new ReplayLiteUploadInitRequest(2048, "1.21.4");
        when(repository.countPendingForServerSince(eq(SERVER_UUID), any())).thenReturn(2L);
        when(storageService.createPresignedUpload(any(), eq(2048L)))
            .thenReturn(new ReplayLiteStorageService.PresignedUpload(
                "https://uploads.example/replay",
                "PUT",
                java.util.Map.of("Content-Type", "application/octet-stream"),
                NOW.plusSeconds(900)
            ));

        service.initUpload(authenticatedServer(), request, "203.0.113.10");

        ArgumentCaptor<ReplayLiteDocument> documentCaptor = ArgumentCaptor.forClass(ReplayLiteDocument.class);
        org.mockito.InOrder inOrder = inOrder(repository, storageService);
        inOrder.verify(repository).saveEntity(documentCaptor.capture());
        inOrder.verify(storageService).createPresignedUpload(eq(documentCaptor.getValue().getObjectKey()), eq(2048L));
        assertEquals(SERVER_UUID, documentCaptor.getValue().getPluginServerUuid());
        assertEquals(ReplayLiteStatus.PENDING, documentCaptor.getValue().getStatus());
        assertEquals("203.0.113.10", documentCaptor.getValue().getUploadInitIp());
    }

    @Test
    void confirmUploadEnforcesOneHundredConfirmedReplaysPerServerPerUtcDay() {
        ReplayLiteDocument document = pendingDocument(NOW.minusSeconds(60), MAX_SIZE);
        when(repository.findByReplayId(document.getId())).thenReturn(Optional.of(document));
        when(storageService.headObject(document.getObjectKey()))
            .thenReturn(Optional.of(new ReplayLiteStorageService.ObjectMetadata(MAX_SIZE, NOW)));
        when(quotaRepository.reserveConfirmedUpload(eq(SERVER_UUID), any(), eq(100), eq(NOW))).thenReturn(false);

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> service.confirmUpload(authenticatedServer(), document.getId(), "203.0.113.10")
        );

        assertEquals("Replay Lite daily upload limit reached", exception.getMessage());
        verify(repository, never()).saveEntity(any());
    }

    @Test
    void confirmUploadFailsWhenObjectIsMissing() {
        ReplayLiteDocument document = pendingDocument(NOW.minusSeconds(60), MAX_SIZE);
        when(repository.findByReplayId(document.getId())).thenReturn(Optional.of(document));
        when(storageService.headObject(document.getObjectKey())).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> service.confirmUpload(authenticatedServer(), document.getId(), "203.0.113.10")
        );

        assertEquals("Replay upload object was not found", exception.getMessage());
        verify(repository, never()).saveEntity(any());
    }

    @Test
    void confirmUploadHidesReplayOwnedByDifferentAuthenticatedServer() {
        ReplayLiteDocument document = pendingDocument(NOW.minusSeconds(60), MAX_SIZE);
        document.setPluginServerUuid(UUID.randomUUID());
        when(repository.findByReplayId(document.getId())).thenReturn(Optional.of(document));

        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> service.confirmUpload(authenticatedServer(), document.getId(), "203.0.113.10")
        );

        assertEquals("Replay not found", exception.getMessage());
        verify(storageService, never()).headObject(any());
        verifyNoInteractions(quotaRepository);
    }

    @Test
    void confirmUploadFailsWhenObjectIsOversized() {
        ReplayLiteDocument document = pendingDocument(NOW.minusSeconds(60), MAX_SIZE);
        when(repository.findByReplayId(document.getId())).thenReturn(Optional.of(document));
        when(storageService.headObject(document.getObjectKey()))
            .thenReturn(Optional.of(new ReplayLiteStorageService.ObjectMetadata(MAX_SIZE + 1, NOW)));

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> service.confirmUpload(authenticatedServer(), document.getId(), "203.0.113.10")
        );

        assertEquals("Replay Lite upload exceeds 10 MB", exception.getMessage());
        verify(repository, never()).saveEntity(any());
    }

    @Test
    void confirmUploadFailsWhenPendingUploadIsStale() {
        ReplayLiteDocument document = pendingDocument(NOW.minusSeconds(16 * 60), MAX_SIZE);
        when(repository.findByReplayId(document.getId())).thenReturn(Optional.of(document));

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> service.confirmUpload(authenticatedServer(), document.getId(), "203.0.113.10")
        );

        assertEquals("Replay Lite upload has expired", exception.getMessage());
        verify(storageService, never()).headObject(any());
    }

    @Test
    void publicReplayHidesPendingAndExpiredReplays() {
        ReplayLiteDocument pending = pendingDocument(NOW.minusSeconds(60), 1024);
        when(repository.findByReplayId("pending")).thenReturn(Optional.of(pending));

        ReplayLiteDocument expired = confirmedDocument(NOW.minusSeconds(25 * 60 * 60), 1024);
        expired.setId("expired");
        expired.setExpiresAt(NOW.minusSeconds(60));
        when(repository.findByReplayId("expired")).thenReturn(Optional.of(expired));

        assertTrue(service.getPublicReplay("pending").isEmpty());
        assertTrue(service.getPublicReplay("expired").isEmpty());
    }

    @Test
    void publicReplayExposesOnlyViewerFieldsForConfirmedReplay() {
        ReplayLiteDocument document = confirmedDocument(NOW.minusSeconds(60), 2048);
        when(repository.findByReplayId(document.getId())).thenReturn(Optional.of(document));

        Optional<ReplayLitePublicResponse> response = service.getPublicReplay(document.getId());

        assertTrue(response.isPresent());
        assertEquals(document.getId(), response.get().replayId());
        assertEquals("1.21.4", response.get().mcVersion());
        assertEquals(2048, response.get().fileSize());
        assertEquals(ReplayLiteStatus.CONFIRMED.name(), response.get().status());
        assertFalse(response.get().labeled());
        assertEquals("/v1/public/replay-lite/replays/" + document.getId() + "/download", response.get().replayUrl());
        assertFalse(response.get().replayUrl().contains(document.getObjectKey()));
        verify(storageService, never()).getPublicUrl(any());
    }

    @Test
    void publicReplayDownloadStreamsObjectForConfirmedReplay() {
        ReplayLiteDocument document = confirmedDocument(NOW.minusSeconds(60), 2048);
        when(repository.findByReplayId(document.getId())).thenReturn(Optional.of(document));
        byte[] replayBytes = new byte[] {1, 2, 3};
        when(storageService.downloadObject(document.getObjectKey(), MAX_SIZE))
            .thenReturn(Optional.of(new ReplayLiteStorageService.DownloadedObject(replayBytes, "application/octet-stream")));

        Optional<ReplayLiteService.ReplayLiteDownload> download = service.getPublicReplayDownload(document.getId());

        assertTrue(download.isPresent());
        assertArrayEquals(replayBytes, download.get().bytes());
        assertEquals("application/octet-stream", download.get().contentType());
        assertEquals(document.getExpiresAt(), download.get().expiresAt());
        verify(storageService, never()).getPublicUrl(any());
    }

    @Test
    void publicReplayDownloadHidesPendingAndExpiredReplays() {
        ReplayLiteDocument pending = pendingDocument(NOW.minusSeconds(60), 1024);
        when(repository.findByReplayId("pending")).thenReturn(Optional.of(pending));

        ReplayLiteDocument expired = confirmedDocument(NOW.minusSeconds(25 * 60 * 60), 1024);
        expired.setId("expired");
        expired.setExpiresAt(NOW.minusSeconds(60));
        when(repository.findByReplayId("expired")).thenReturn(Optional.of(expired));

        assertTrue(service.getPublicReplayDownload("pending").isEmpty());
        assertTrue(service.getPublicReplayDownload("expired").isEmpty());
        verify(storageService, never()).downloadObject(any(), eq(MAX_SIZE));
    }

    @Test
    void labelsStoreNormalizedVerdictsOnReplayLiteDocumentWithoutTrainingCoupling() {
        ReplayLiteDocument document = confirmedDocument(NOW.minusSeconds(60), 2048);
        when(repository.findByReplayId(document.getId())).thenReturn(Optional.of(document));
        when(repository.claimLabels(eq(document.getId()), eq(NOW), any(), eq("203.0.113.10"))).thenReturn(true);

        ReplayLiteLabel label = new ReplayLiteLabel("player", "legit", List.of(), "notes");

        service.submitLabels(document.getId(), List.of(label), "203.0.113.10");

        ArgumentCaptor<List<ReplayLiteLabel>> labelsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).claimLabels(eq(document.getId()), eq(NOW), labelsCaptor.capture(), eq("203.0.113.10"));
        assertEquals(List.of(new ReplayLiteLabel("player", "LEGIT", List.of(), "notes")), labelsCaptor.getValue());
        verify(repository, never()).saveEntity(any());
    }

    @Test
    void labelsReturnConflictWhenAtomicClaimLosesRace() {
        ReplayLiteDocument document = confirmedDocument(NOW.minusSeconds(60), 2048);
        when(repository.findByReplayId(document.getId())).thenReturn(Optional.of(document));
        when(repository.claimLabels(eq(document.getId()), eq(NOW), any(), eq("203.0.113.10"))).thenReturn(false);

        ReplayLiteLabel label = new ReplayLiteLabel("player", "legit", List.of(), "notes");

        ConflictException exception = assertThrows(
            ConflictException.class,
            () -> service.submitLabels(document.getId(), List.of(label), "203.0.113.10")
        );

        assertEquals("This replay has already been labeled", exception.getMessage());
        verify(repository, never()).saveEntity(any());
    }

    @Test
    void labelsRejectUnsupportedVerdicts() {
        ReplayLiteDocument document = confirmedDocument(NOW.minusSeconds(60), 2048);
        when(repository.findByReplayId(document.getId())).thenReturn(Optional.of(document));

        ReplayLiteLabel label = new ReplayLiteLabel("player", "cheating", List.of(), "notes");

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> service.submitLabels(document.getId(), List.of(label), "203.0.113.10")
        );

        assertEquals("Replay Lite label verdict is invalid", exception.getMessage());
        verify(repository, never()).saveEntity(any());
    }

    @Test
    void concurrentConfirmsUseQuotaReservationAtSaveBoundary() throws Exception {
        ReplayLiteDocument first = pendingDocument(NOW.minusSeconds(60), 2048);
        first.setId("first");
        ReplayLiteDocument second = pendingDocument(NOW.minusSeconds(60), 2048);
        second.setId("second");

        when(repository.findByReplayId("first")).thenReturn(Optional.of(first));
        when(repository.findByReplayId("second")).thenReturn(Optional.of(second));

        CyclicBarrier storageBarrier = new CyclicBarrier(2);
        when(storageService.headObject(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            awaitBarrier(storageBarrier);
            return Optional.of(new ReplayLiteStorageService.ObjectMetadata(2048, NOW));
        });

        AtomicInteger reservedCount = new AtomicInteger(0);
        when(quotaRepository.reserveConfirmedUpload(eq(SERVER_UUID), any(), eq(100), eq(NOW)))
            .thenAnswer(invocation -> reservedCount.incrementAndGet() == 1);
        when(repository.confirmPendingUpload(
            any(),
            eq(2048L),
            eq(NOW),
            eq(NOW.plusSeconds(24 * 60 * 60)),
            eq("203.0.113.10"),
            any()
        )).thenReturn(true);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        Runnable confirm = () -> {
            ready.countDown();
            awaitLatch(start);
            try {
                String replayId = Thread.currentThread().getName();
                service.confirmUpload(authenticatedServer(), replayId, "203.0.113.10");
            } catch (Throwable throwable) {
                failures.add(throwable);
            }
        };

        Thread firstThread = new Thread(confirm, "first");
        Thread secondThread = new Thread(confirm, "second");
        firstThread.start();
        secondThread.start();

        assertTrue(ready.await(1, TimeUnit.SECONDS));
        start.countDown();
        firstThread.join(2000);
        secondThread.join(2000);

        assertEquals(2, reservedCount.get());
        assertEquals(1, failures.size());
        assertTrue(failures.getFirst() instanceof ValidationException);
        assertEquals("Replay Lite daily upload limit reached", failures.getFirst().getMessage());
    }

    @Test
    void duplicateConcurrentConfirmOfSameReplayReleasesLostQuotaReservation() throws Exception {
        ReplayLiteDocument document = pendingDocument(NOW.minusSeconds(60), 2048);
        when(repository.findByReplayId(document.getId())).thenReturn(Optional.of(document));
        when(storageService.headObject(document.getObjectKey()))
            .thenReturn(Optional.of(new ReplayLiteStorageService.ObjectMetadata(2048, NOW)));
        when(quotaRepository.reserveConfirmedUpload(eq(SERVER_UUID), any(), eq(100), eq(NOW))).thenReturn(true);

        AtomicInteger transitionAttempts = new AtomicInteger(0);
        when(repository.confirmPendingUpload(
            eq(document.getId()),
            eq(2048L),
            eq(NOW),
            eq(NOW.plusSeconds(24 * 60 * 60)),
            eq("203.0.113.10"),
            any()
        )).thenAnswer(invocation -> transitionAttempts.incrementAndGet() == 1);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        Runnable confirm = () -> {
            ready.countDown();
            awaitLatch(start);
            try {
                service.confirmUpload(authenticatedServer(), document.getId(), "203.0.113.10");
            } catch (Throwable throwable) {
                failures.add(throwable);
            }
        };

        Thread firstThread = new Thread(confirm, "same-replay-1");
        Thread secondThread = new Thread(confirm, "same-replay-2");
        firstThread.start();
        secondThread.start();

        assertTrue(ready.await(1, TimeUnit.SECONDS));
        start.countDown();
        firstThread.join(2000);
        secondThread.join(2000);

        assertEquals(2, transitionAttempts.get());
        assertEquals(1, failures.size());
        assertTrue(failures.getFirst() instanceof ConflictException);
        assertEquals("Replay Lite upload is not pending", failures.getFirst().getMessage());
        verify(quotaRepository, times(2)).reserveConfirmedUpload(eq(SERVER_UUID), any(), eq(100), eq(NOW));
        verify(quotaRepository).releaseConfirmedUpload(eq(SERVER_UUID), any(), eq(NOW));
    }

    private ReplayLiteDocument pendingDocument(Instant createdAt, long requestedSize) {
        ReplayLiteDocument document = new ReplayLiteDocument();
        document.setId("75f4b741-67df-414c-957b-a8a08222fc30");
        document.setPluginServerUuid(SERVER_UUID);
        document.setObjectKey("replay-lite/20260424/75f4b741-67df-414c-957b-a8a08222fc30.modlreplay");
        document.setStatus(ReplayLiteStatus.PENDING);
        document.setRequestedSize(requestedSize);
        document.setMcVersion("1.21.4");
        document.setCreatedAt(createdAt);
        document.setUploadInitIp("203.0.113.10");
        return document;
    }

    private ReplayLiteDocument confirmedDocument(Instant confirmedAt, long confirmedSize) {
        ReplayLiteDocument document = pendingDocument(NOW.minusSeconds(60), confirmedSize);
        document.setStatus(ReplayLiteStatus.CONFIRMED);
        document.setConfirmedSize(confirmedSize);
        document.setConfirmedAt(confirmedAt);
        document.setExpiresAt(confirmedAt.plusSeconds(24 * 60 * 60));
        document.setConfirmIp("203.0.113.10");
        return document;
    }

    private Server authenticatedServer() {
        Server server = new Server("Demo", "demo", "server_demo", "admin@example.com", true, ServerPlan.FREE);
        server.setId(SERVER_ID);
        return server;
    }

    private void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            assertTrue(latch.await(1, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
