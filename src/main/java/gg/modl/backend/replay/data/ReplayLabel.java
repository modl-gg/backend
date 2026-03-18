package gg.modl.backend.replay.data;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReplayLabel {
    private String uuid;
    private String playerName;
    private String verdict;
    private int confidence;
    private List<CheatDetail> cheats;
    private String notes;

    @Data
    @NoArgsConstructor
    public static class CheatDetail {
        private String type;
        private List<TimeRange> timeRanges;
    }

    @Data
    @NoArgsConstructor
    public static class TimeRange {
        private long startMs;
        private long endMs;
    }
}
