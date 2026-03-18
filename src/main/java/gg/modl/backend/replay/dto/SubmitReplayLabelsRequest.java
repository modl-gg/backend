package gg.modl.backend.replay.dto;

import gg.modl.backend.replay.data.ReplayLabel;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SubmitReplayLabelsRequest(
    @NotEmpty List<ReplayLabel> players
) {}
