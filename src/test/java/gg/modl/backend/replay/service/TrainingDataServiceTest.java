package gg.modl.backend.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.TrainingSegmentRepository;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.replay.data.ReplayLabel;
import gg.modl.backend.replay.data.TrainingSegmentDocument;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.minecraft.replay.ReplayReader;
import gg.modl.minecraft.replay.ReplayWriter;
import gg.modl.minecraft.replay.format.ReplayEvent;
import gg.modl.minecraft.replay.format.ReplayHeader;
import gg.modl.minecraft.replay.format.events.PlayerMoveEvent;
import gg.modl.minecraft.replay.util.BlockSnapshot;
import gg.modl.minecraft.replay.util.FormatConstants;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrainingDataServiceTest {
    private static final UUID PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private TrainingSegmentRepository trainingSegmentRepository;

    private TrainingDataService trainingDataService;
    private Server server;
    private ReplayDocument replay;

    @BeforeEach
    void setUp() {
        trainingDataService = new TrainingDataService(s3StorageService, trainingSegmentRepository);
        server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        replay = new ReplayDocument();
        replay.setId("replay-1");
        replay.setStorageKey("replays/replay-1.modlreplay");
        replay.setMcVersion("1.21.4");
    }

    @Test
    void supportsTrainingReplayFormatAllowsOnlyV4() {
        assertFalse(TrainingDataService.supportsTrainingReplayFormat(replayHeader(3)));
        assertTrue(TrainingDataService.supportsTrainingReplayFormat(replayHeader(4)));
        assertFalse(TrainingDataService.supportsTrainingReplayFormat(replayHeader(5)));
    }

    @Test
    void generateSegmentsAsyncDoesNotSaveSegmentsForReplayFormatV3() throws IOException {
        byte[] replayBytes = replayBytes(3);
        assertReadableReplay(replayBytes, 3);
        when(s3StorageService.downloadBytes(replay.getStorageKey())).thenReturn(replayBytes);

        trainingDataService.generateSegmentsAsync(server, replay, List.of(cheatingLabel()));

        verify(trainingSegmentRepository, never()).save(any());
    }

    @Test
    void generateSegmentsAsyncDoesNotSaveSegmentsForReplayFormatV5() throws IOException {
        when(s3StorageService.downloadBytes(replay.getStorageKey())).thenReturn(replayBytes(5));

        trainingDataService.generateSegmentsAsync(server, replay, List.of(cheatingLabel()));

        verify(trainingSegmentRepository, never()).save(any());
    }

    @Test
    void generateSegmentsAsyncSavesCheatingIntervalSegmentForReplayFormatV4() throws IOException {
        when(s3StorageService.downloadBytes(replay.getStorageKey())).thenReturn(replayBytes(4));
        ArgumentCaptor<TrainingSegmentDocument> segmentCaptor =
            ArgumentCaptor.forClass(TrainingSegmentDocument.class);

        trainingDataService.generateSegmentsAsync(server, replay, List.of(cheatingLabel()));

        verify(trainingSegmentRepository).save(segmentCaptor.capture());
        TrainingSegmentDocument segment = segmentCaptor.getValue();
        assertEquals("replay-1", segment.getReplayId());
        assertEquals("server", segment.getServerName());
        assertEquals("db", segment.getServerDatabaseName());
        assertEquals(PLAYER_UUID.toString(), segment.getPlayerUuid());
        assertEquals("Player", segment.getPlayerName());
        assertEquals("cheating", segment.getVerdict());
        assertEquals("aim", segment.getCheatType());
        assertEquals(95, segment.getConfidence());
        assertEquals("reviewed", segment.getNotes());
        assertEquals(500, segment.getStartMs());
        assertEquals(1500, segment.getEndMs());
        assertEquals("1.21.4", segment.getMcVersion());
        assertNotNull(segment.getCreatedAt());
        assertNotNull(segment.getSegmentBinary());
        assertParsedSegment(segment.getSegmentBinary().getData());
    }

    private static ReplayLabel cheatingLabel() {
        ReplayLabel.TimeRange range = new ReplayLabel.TimeRange();
        range.setStartMs(500);
        range.setEndMs(1500);

        ReplayLabel.CheatDetail cheat = new ReplayLabel.CheatDetail();
        cheat.setType("aim");
        cheat.setTimeRanges(List.of(range));

        ReplayLabel label = new ReplayLabel();
        label.setUuid(PLAYER_UUID.toString());
        label.setPlayerName("Player");
        label.setVerdict("cheating");
        label.setConfidence(95);
        label.setNotes("reviewed");
        label.setCheats(List.of(cheat));
        return label;
    }

    private static byte[] replayBytes(int formatVersion) throws IOException {
        if (formatVersion == 3) {
            return replayV3Bytes();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ReplayWriter writer = new ReplayWriter(output)) {
            writer.writeHeader(replayHeader(formatVersion));
            writer.writeSnapshot(List.of(new BlockSnapshot(0, (short) 64, 0, 1)));
            writer.writeEvent(new PlayerMoveEvent(1000, PLAYER_UUID, 0.5F, 64.0F, 0.5F, 0.0F, 0.0F));
            writer.flush();
        }
        return output.toByteArray();
    }

    private static byte[] replayV3Bytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(output)) {
            ReplayHeader header = replayHeader(3);
            byte[] mcVersion = header.getMcVersion().getBytes(StandardCharsets.UTF_8);

            out.write(FormatConstants.MAGIC);
            out.writeShort(header.getVersion());
            out.writeLong(header.getStartTime());
            out.writeShort(mcVersion.length);
            out.write(mcVersion);
            out.writeInt(header.getTargetX());
            out.writeInt(header.getTargetY());
            out.writeInt(header.getTargetZ());
            out.writeInt(header.getRadiusBlocks());

            out.writeInt(1);
            out.writeInt(0);
            out.writeShort(64);
            out.writeInt(0);
            out.writeInt(1);

            out.writeByte(ReplayEvent.EventType.PLAYER_MOVE.getId());
            out.writeInt(1000);
            out.writeLong(PLAYER_UUID.getMostSignificantBits());
            out.writeLong(PLAYER_UUID.getLeastSignificantBits());
            out.writeFloat(0.5F);
            out.writeFloat(64.0F);
            out.writeFloat(0.5F);
            out.writeShort(FormatConstants.encodeAngle(0.0F));
            out.writeShort(FormatConstants.encodeAngle(0.0F));
        }
        return output.toByteArray();
    }

    private static ReplayHeader replayHeader(int formatVersion) {
        return ReplayHeader.builder()
            .version(formatVersion)
            .startTime(1L)
            .mcVersion("1.21.4")
            .targetX(0)
            .targetY(64)
            .targetZ(0)
            .radiusBlocks(16)
            .build();
    }

    private static void assertParsedSegment(byte[] segmentBytes) throws IOException {
        assertNotNull(segmentBytes);
        try (ReplayReader reader = new ReplayReader(new ByteArrayInputStream(segmentBytes))) {
            ReplayHeader header = reader.readHeader();
            assertEquals(4, header.getVersion());
            assertEquals("1.21.4", header.getMcVersion());
            assertFalse(reader.readSnapshot().isEmpty());

            List<ReplayEvent> events = new ArrayList<>();
            ReplayEvent event;
            while ((event = reader.readEvent()) != null) {
                events.add(event);
            }
            assertFalse(events.isEmpty());
            assertEquals(500, events.get(0).getTimestampDeltaMs());
        }
    }

    private static void assertReadableReplay(byte[] replayBytes, int expectedVersion) throws IOException {
        try (ReplayReader reader = new ReplayReader(new ByteArrayInputStream(replayBytes))) {
            ReplayHeader header = reader.readHeader();
            assertEquals(expectedVersion, header.getVersion());
            assertFalse(reader.readSnapshot().isEmpty());

            ReplayEvent event = reader.readEvent();
            assertNotNull(event);
            assertEquals(ReplayEvent.EventType.PLAYER_MOVE, event.getType());
            assertEquals(1000, event.getTimestampDeltaMs());
            assertNull(reader.readEvent());
        }
    }
}
