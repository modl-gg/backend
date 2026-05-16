package gg.modl.backend.replay.dto;

import gg.modl.backend.replay.data.ReplayLabel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SubmitReplayLabelsRequest(
    @NotEmpty @Size(max = 50) List<@Valid ReplayLabel> players
) {}
