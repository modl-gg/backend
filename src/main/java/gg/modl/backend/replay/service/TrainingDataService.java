package gg.modl.backend.replay.service;

import gg.modl.backend.database.mongo.repository.TrainingSegmentRepository;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.replay.data.ReplayLabel;
import gg.modl.backend.replay.data.TrainingSegmentDocument;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.minecraft.replay.ReplayReader;
import gg.modl.minecraft.replay.ReplayWriter;
import gg.modl.minecraft.replay.format.ReplayEvent;
import gg.modl.minecraft.replay.format.ReplayHeader;
import gg.modl.minecraft.replay.format.events.PlayerMoveEvent;
import gg.modl.minecraft.replay.format.events.PlayerSpawnEvent;
import gg.modl.minecraft.replay.util.BlockSnapshot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Binary;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrainingDataService {
    private final S3StorageService s3StorageService;
    private final TrainingSegmentRepository trainingSegmentRepository;

    private static final int BLOCK_FILTER_RADIUS = 16;
    private static final int SUPPORTED_TRAINING_REPLAY_FORMAT_VERSION = 4;
    private static final int MAX_SEGMENTS_PER_SUBMISSION = 64;

    private static final String VERDICT_UNSURE = "unsure";
    private static final String VERDICT_CHEATING = "cheating";
    private static final String VERDICT_LEGIT = "legit";

    @Async
    public void generateSegmentsAsync(Server server, ReplayDocument doc, List<ReplayLabel> labels) {
        try {
            generateSegments(server, doc, labels);
        } catch (Exception e) {
            log.error("Failed to generate training segments for replay {} on server {}",
                doc.getId(), server.getDatabaseName(), e);
        }
    }

    private void generateSegments(Server server, ReplayDocument doc, List<ReplayLabel> labels) throws IOException {
        byte[] replayBytes = s3StorageService.downloadBytes(doc.getStorageKey());

        ReplayHeader header;
        List<BlockSnapshot> snapshot;
        List<ReplayEvent> allEvents = new ArrayList<>();

        try (ReplayReader reader = new ReplayReader(new ByteArrayInputStream(replayBytes))) {
            header = reader.readHeader();
            if (!supportsTrainingReplayFormat(header)) {
                log.warn(
                    "Skipping training segment generation for replay {} on server {}: unsupported replay format version {}",
                    doc.getId(), server.getDatabaseName(), header.getVersion()
                );
                return;
            }

            snapshot = reader.readSnapshot();

            ReplayEvent event;
            while ((event = reader.readEvent()) != null) {
                allEvents.add(event);
            }
        }

        EventIndex index = indexEvents(allEvents);
        List<SegmentSpec> specs = collectSpecs(labels, index.maxTimestampMs());
        trainingSegmentRepository.deleteByReplayId(server.getDatabaseName(), doc.getId());
        for (SegmentSpec spec : specs) {
            trainingSegmentRepository.save(buildSegment(header, snapshot, allEvents, index, server, doc, spec));
        }

        log.debug("Generated {} training segments for replay {} on server {}",
            specs.size(), doc.getId(), server.getDatabaseName());
    }

    static boolean supportsTrainingReplayFormat(ReplayHeader header) {
        return header.getVersion() == SUPPORTED_TRAINING_REPLAY_FORMAT_VERSION;
    }

    private EventIndex indexEvents(List<ReplayEvent> allEvents) {
        long[] timestamps = new long[allEvents.size()];
        Map<UUID, List<PlayerPositionSample>> positionsByPlayer = new HashMap<>();
        long maxTimestampMs = 0;

        for (int i = 0; i < allEvents.size(); i++) {
            ReplayEvent event = allEvents.get(i);
            long timestampMs = event.getTimestampDeltaMs();
            timestamps[i] = timestampMs;
            if (timestampMs > maxTimestampMs) {
                maxTimestampMs = timestampMs;
            }
            if (event instanceof PlayerMoveEvent move) {
                recordPosition(positionsByPlayer, move.getUuid(), timestampMs, move.getX(), move.getZ());
            } else if (event instanceof PlayerSpawnEvent spawn) {
                recordPosition(positionsByPlayer, spawn.getUuid(), timestampMs, spawn.getX(), spawn.getZ());
            }
        }

        return new EventIndex(timestamps, positionsByPlayer, maxTimestampMs);
    }

    private void recordPosition(
        Map<UUID, List<PlayerPositionSample>> positionsByPlayer,
        UUID uuid,
        long timestampMs,
        float x,
        float z
    ) {
        positionsByPlayer.computeIfAbsent(uuid, key -> new ArrayList<>())
            .add(new PlayerPositionSample(timestampMs, (int) Math.floor(x), (int) Math.floor(z)));
    }

    private List<SegmentSpec> collectSpecs(List<ReplayLabel> labels, long maxTimestampMs) {
        List<SegmentSpec> specs = new ArrayList<>();
        boolean truncated = false;
        for (ReplayLabel label : labels) {
            if (specs.size() >= MAX_SEGMENTS_PER_SUBMISSION) {
                truncated = true;
                break;
            }
            truncated |= appendLabelSpecs(specs, label, maxTimestampMs);
        }
        if (truncated) {
            log.warn("Training label submission exceeded the {}-segment cap; extra segments were dropped",
                MAX_SEGMENTS_PER_SUBMISSION);
        }
        return specs;
    }

    private boolean appendLabelSpecs(List<SegmentSpec> specs, ReplayLabel label, long maxTimestampMs) {
        String verdict = label.getVerdict();
        if (VERDICT_UNSURE.equals(verdict)) {
            return false;
        }

        UUID playerUuid = parsePlayerUuid(label.getUuid());
        if (playerUuid == null) {
            return false;
        }

        if (VERDICT_CHEATING.equals(verdict)) {
            return appendCheatingSpecs(specs, label, playerUuid);
        }
        if (VERDICT_LEGIT.equals(verdict)) {
            return !addSpec(specs, new SegmentSpec(playerUuid, label, null, 0, maxTimestampMs));
        }
        return false;
    }

    private boolean appendCheatingSpecs(List<SegmentSpec> specs, ReplayLabel label, UUID playerUuid) {
        if (label.getCheats() == null) {
            return false;
        }

        Map<String, List<long[]>> rangesByType = new LinkedHashMap<>();
        for (ReplayLabel.CheatDetail cheat : label.getCheats()) {
            if (cheat.getTimeRanges() == null) {
                continue;
            }
            List<long[]> ranges = rangesByType.computeIfAbsent(cheat.getType(), key -> new ArrayList<>());
            for (ReplayLabel.TimeRange range : cheat.getTimeRanges()) {
                ranges.add(new long[]{range.getStartMs(), range.getEndMs()});
            }
        }

        for (Map.Entry<String, List<long[]>> entry : rangesByType.entrySet()) {
            for (long[] range : mergeRanges(entry.getValue())) {
                if (!addSpec(specs, new SegmentSpec(playerUuid, label, entry.getKey(), range[0], range[1]))) {
                    return true;
                }
            }
        }
        return false;
    }

    private UUID parsePlayerUuid(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            log.warn("Skipping training label with malformed player UUID");
            return null;
        }
    }

    private boolean addSpec(List<SegmentSpec> specs, SegmentSpec spec) {
        if (specs.size() >= MAX_SEGMENTS_PER_SUBMISSION) {
            return false;
        }
        specs.add(spec);
        return true;
    }

    private List<long[]> mergeRanges(List<long[]> ranges) {
        if (ranges.isEmpty()) {
            return List.of();
        }

        List<long[]> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparingLong(range -> range[0]));

        List<long[]> merged = new ArrayList<>();
        long[] current = null;
        for (long[] range : sorted) {
            if (current != null && range[0] <= current[1]) {
                current[1] = Math.max(current[1], range[1]);
            } else {
                current = new long[]{range[0], range[1]};
                merged.add(current);
            }
        }
        return merged;
    }

    private TrainingSegmentDocument buildSegment(
        ReplayHeader header,
        List<BlockSnapshot> snapshot,
        List<ReplayEvent> allEvents,
        EventIndex index,
        Server server,
        ReplayDocument doc,
        SegmentSpec spec
    ) throws IOException {
        long[] timestamps = index.timestamps();
        List<ReplayEvent> segmentEvents = allEvents.subList(
            lowerBound(timestamps, spec.startMs()),
            upperBound(timestamps, spec.endMs())
        );

        Set<Long> playerBlockPositions = collectPlayerBlockPositions(index, spec);

        List<BlockSnapshot> filteredBlocks;
        if (playerBlockPositions.isEmpty()) {
            filteredBlocks = snapshot;
        } else {
            filteredBlocks = new ArrayList<>();
            for (BlockSnapshot block : snapshot) {
                if (isWithinRadius(block.getX(), block.getZ(), playerBlockPositions)) {
                    filteredBlocks.add(block);
                }
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ReplayWriter writer = new ReplayWriter(baos)) {
            writer.writeHeader(header);
            writer.writeSnapshot(filteredBlocks);
            for (ReplayEvent event : segmentEvents) {
                writer.writeEvent(event, spec.startMs());
            }
            writer.flush();
        }

        TrainingSegmentDocument segment = new TrainingSegmentDocument();
        segment.setReplayId(doc.getId());
        segment.setServerName(server.getServerName());
        segment.setServerDatabaseName(server.getDatabaseName());
        segment.setPlayerUuid(spec.playerUuid().toString());
        segment.setPlayerName(spec.label().getPlayerName());
        segment.setVerdict(spec.label().getVerdict());
        segment.setCheatType(spec.cheatType());
        segment.setConfidence(spec.label().getConfidence());
        segment.setNotes(spec.label().getNotes());
        segment.setStartMs(spec.startMs());
        segment.setEndMs(spec.endMs());
        segment.setMcVersion(doc.getMcVersion());
        segment.setSegmentBinary(new Binary(baos.toByteArray()));
        segment.setCreatedAt(new Date());

        return segment;
    }

    private Set<Long> collectPlayerBlockPositions(EventIndex index, SegmentSpec spec) {
        List<PlayerPositionSample> samples = index.positionsByPlayer().get(spec.playerUuid());
        if (samples == null) {
            return Set.of();
        }

        Set<Long> positions = new HashSet<>();
        for (PlayerPositionSample sample : samples) {
            if (sample.timestampMs() > spec.endMs()) {
                break;
            }
            positions.add(packXZ(sample.blockX(), sample.blockZ()));
        }
        return positions;
    }

    private boolean isWithinRadius(int blockX, int blockZ, Set<Long> playerBlockPositions) {
        for (long packed : playerBlockPositions) {
            int px = unpackX(packed);
            int pz = unpackZ(packed);
            if (Math.abs(blockX - px) <= BLOCK_FILTER_RADIUS && Math.abs(blockZ - pz) <= BLOCK_FILTER_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private static int lowerBound(long[] timestamps, long key) {
        int lo = 0;
        int hi = timestamps.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (timestamps[mid] < key) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static int upperBound(long[] timestamps, long key) {
        int lo = 0;
        int hi = timestamps.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (timestamps[mid] <= key) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }

    private record SegmentSpec(UUID playerUuid, ReplayLabel label, String cheatType, long startMs, long endMs) {}

    private record PlayerPositionSample(long timestampMs, int blockX, int blockZ) {}

    private record EventIndex(long[] timestamps, Map<UUID, List<PlayerPositionSample>> positionsByPlayer, long maxTimestampMs) {}
}
