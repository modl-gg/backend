package gg.modl.backend.replay.service;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.replay.dto.PlayerReplayResponse;
import gg.modl.backend.replay.util.ReplayReferenceUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageMetadataService;
import gg.modl.backend.ticket.data.Ticket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlayerReplayListingService {
    private final ReplayMongoRepository replayRepository;
    private final S3StorageService s3StorageService;
    private final StorageMetadataService storageMetadataService;
    private final TicketMongoRepository ticketRepository;

    public List<PlayerReplayResponse> listPlayerReplays(Server server, String playerUuid) {
        String normalizedPlayerUuid = ReplayReferenceUtil.requireValidUuid(playerUuid);
        Map<String, PlayerReplayResponse> responses = new LinkedHashMap<>();

        List<ReplayDocument> directReplays = replayRepository.findByTargetUuid(server, normalizedPlayerUuid, 100);
        Set<String> orphanedReplayIds = orphanedCompleteReplayIds(server, directReplays);
        for (ReplayDocument replay : directReplays) {
            if (orphanedReplayIds.contains(replay.getId())) {
                continue;
            }
            PlayerReplayResponse response = toPlayerReplayResponse(replay, PlayerReplayResponse.MatchSource.DIRECT_METADATA);
            responses.put(response.deduplicationKey(), response);
        }

        List<Ticket> tickets = ticketRepository.findPlayerTicketsWithReplayUrl(server, normalizedPlayerUuid, 100);
        for (Ticket ticket : tickets) {
            String replayUrl = ReplayReferenceUtil.normalize(ticket.getReplayUrl());
            if (replayUrl == null) {
                continue;
            }
            String replayId = ReplayReferenceUtil.extractReplayId(replayUrl);
            if (replayId != null && orphanedReplayIds.contains(replayId)) {
                continue;
            }
            if (hasReplayReference(responses, replayUrl, replayId)) {
                continue;
            }
            String key = replayId != null ? PlayerReplayResponse.idKey(replayId) : PlayerReplayResponse.urlKey(replayUrl);
            if (responses.containsKey(key)) {
                continue;
            }
            responses.put(key, PlayerReplayResponse.fromTicket(ticket, replayUrl, replayId));
        }

        return List.copyOf(responses.values());
    }

    private Set<String> orphanedCompleteReplayIds(Server server, List<ReplayDocument> replays) {
        if (!storageMetadataService.isMetadataAuthoritative(server)) {
            return Set.of();
        }
        List<String> completeStorageKeys = new ArrayList<>();
        for (ReplayDocument replay : replays) {
            if (isCompleteWithStorageKey(replay)) {
                completeStorageKeys.add(replay.getStorageKey());
            }
        }
        if (completeStorageKeys.isEmpty()) {
            return Set.of();
        }

        Set<String> existingStorageKeys = storageMetadataService.existingKeys(server, completeStorageKeys);
        if (existingStorageKeys.size() == completeStorageKeys.size()) {
            return Set.of();
        }

        Set<String> orphanedReplayIds = new HashSet<>();
        for (ReplayDocument replay : replays) {
            if (isCompleteWithStorageKey(replay) && !existingStorageKeys.contains(replay.getStorageKey())) {
                orphanedReplayIds.add(replay.getId());
            }
        }
        return orphanedReplayIds;
    }

    private boolean isCompleteWithStorageKey(ReplayDocument replay) {
        return ReplayDocument.STATUS_COMPLETE.equals(replay.getStatus()) && replay.getStorageKey() != null;
    }

    private PlayerReplayResponse toPlayerReplayResponse(ReplayDocument replay, PlayerReplayResponse.MatchSource matchSource) {
        String replayUrl = ReplayDocument.STATUS_COMPLETE.equals(replay.getStatus()) && replay.getStorageKey() != null
                           ? s3StorageService.getCdnUrl(replay.getStorageKey())
                           : null;
        return new PlayerReplayResponse(
            replay.getId(),
            replay.getTargetUuid(),
            replay.getTargetName(),
            replay.getMcVersion(),
            replay.getFileSize(),
            replay.getCreatedAt(),
            replay.getStatus(),
            replayUrl,
            matchSource
        );
    }

    private boolean hasReplayReference(Map<String, PlayerReplayResponse> responses, String replayUrl, String replayId) {
        return responses.values()
            .stream()
            .anyMatch(response ->
                replayUrl.equals(response.replayUrl())
                || (replayId != null && replayId.equals(response.replayId()))
            );
    }
}
