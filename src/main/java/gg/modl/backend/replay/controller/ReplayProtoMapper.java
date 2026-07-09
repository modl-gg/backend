package gg.modl.backend.replay.controller;

import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.replay.data.ReplayLabel;
import gg.modl.proto.modl.v1.PublicReplayResponse;
import gg.modl.proto.modl.v1.ReplayLabelResponse;
import gg.modl.proto.modl.v1.SubmitReplayLabelsRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReplayProtoMapper {

    private static final int MAX_REPLAY_LABELS = 64;

    public PublicReplayResponse toReplayResponse(gg.modl.backend.replay.dto.PublicReplayResponse replay) {
        return PublicReplayResponse.newBuilder()
            .setReplayId(nullToEmpty(replay.replayId()))
            .setMcVersion(nullToEmpty(replay.mcVersion()))
            .setFileSize(replay.fileSize())
            .setTimestamp(replay.timestamp())
            .setReplayUrl(nullToEmpty(replay.replayUrl()))
            .setStatus(nullToEmpty(replay.status()))
            .setLabeled(replay.labeled())
            .build();
    }

    public ReplayLabelResponse labelResponse(String status) {
        return ReplayLabelResponse.newBuilder()
            .setStatus(nullToEmpty(status))
            .build();
    }

    public List<ReplayLabel> toReplayLabels(SubmitReplayLabelsRequest request) {
        if (request.getPlayersCount() > MAX_REPLAY_LABELS) {
            throw new ValidationException("Too many player labels (max " + MAX_REPLAY_LABELS + ")");
        }
        List<ReplayLabel> labels = new ArrayList<>(request.getPlayersCount());
        for (SubmitReplayLabelsRequest.ReplayLabel player : request.getPlayersList()) {
            labels.add(toReplayLabel(player));
        }
        return labels;
    }

    private ReplayLabel toReplayLabel(SubmitReplayLabelsRequest.ReplayLabel player) {
        ReplayLabel label = new ReplayLabel();
        label.setUuid(player.getUuid());
        label.setPlayerName(player.getPlayerName());
        label.setVerdict(player.getVerdict());
        label.setConfidence(player.getConfidence());
        label.setNotes(player.getNotes());

        List<ReplayLabel.CheatDetail> cheats = new ArrayList<>(player.getCheatsCount());
        for (SubmitReplayLabelsRequest.ReplayLabel.CheatDetail cheat : player.getCheatsList()) {
            cheats.add(toCheatDetail(cheat));
        }
        label.setCheats(cheats);
        return label;
    }

    private ReplayLabel.CheatDetail toCheatDetail(SubmitReplayLabelsRequest.ReplayLabel.CheatDetail cheat) {
        ReplayLabel.CheatDetail detail = new ReplayLabel.CheatDetail();
        detail.setType(cheat.getType());

        List<ReplayLabel.TimeRange> ranges = new ArrayList<>(cheat.getTimeRangesCount());
        for (SubmitReplayLabelsRequest.ReplayLabel.TimeRange range : cheat.getTimeRangesList()) {
            ReplayLabel.TimeRange timeRange = new ReplayLabel.TimeRange();
            timeRange.setStartMs(range.getStartMs());
            timeRange.setEndMs(range.getEndMs());
            ranges.add(timeRange);
        }
        detail.setTimeRanges(ranges);
        return detail;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
