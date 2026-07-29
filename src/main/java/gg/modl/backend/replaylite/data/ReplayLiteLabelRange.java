package gg.modl.backend.replaylite.data;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;

public record ReplayLiteLabelRange(
    @PositiveOrZero long startMs,
    @PositiveOrZero long endMs
) {
    @AssertTrue
    public boolean isEndAfterStart() {
        return endMs >= startMs;
    }
}
