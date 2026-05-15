package gg.modl.backend.replaylite.data;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReplayLiteLabel(
    @NotBlank @Size(max = RequestValidationLimits.REPLAY_LITE_LABEL_PLAYER_NAME_MAX_LENGTH) String playerName,
    @NotBlank
    @Size(max = RequestValidationLimits.REPLAY_LITE_LABEL_VERDICT_MAX_LENGTH)
    @Pattern(regexp = ReplayLiteLabelVerdict.VALIDATION_PATTERN)
    String verdict,
    @Size(max = RequestValidationLimits.REPLAY_LITE_LABEL_RANGES_MAX_ENTRIES) List<@Valid ReplayLiteLabelRange> ranges,
    @Size(max = RequestValidationLimits.REPLAY_LITE_LABEL_NOTES_MAX_LENGTH) String notes
) {}
