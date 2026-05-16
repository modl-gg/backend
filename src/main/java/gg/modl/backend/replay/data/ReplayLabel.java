package gg.modl.backend.replay.data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReplayLabel {
    private static final long MAX_TIME_RANGE_MS = 24L * 60L * 60L * 1000L;
    @NotBlank
    @Size(max = 64)
    private String uuid;
    @Size(max = 64)
    private String playerName;
    @NotBlank
    @Size(max = 32)
    private String verdict;
    @Min(0)
    @Max(100)
    private int confidence;
    @Size(max = 20)
    private List<@Valid CheatDetail> cheats;
    @Size(max = 2000)
    private String notes;

    @Data
    @NoArgsConstructor
    public static class CheatDetail {
        @NotBlank
        @Size(max = 64)
        private String type;
        @Size(max = 50)
        private List<@Valid TimeRange> timeRanges;
    }

    @Data
    @NoArgsConstructor
    public static class TimeRange {
        @Min(0)
        @Max(MAX_TIME_RANGE_MS)
        private long startMs;
        @Min(0)
        @Max(MAX_TIME_RANGE_MS)
        private long endMs;

        @AssertTrue(message = "endMs must be greater than or equal to startMs")
        public boolean isRangeValid() {
            return endMs >= startMs;
        }
    }
}
