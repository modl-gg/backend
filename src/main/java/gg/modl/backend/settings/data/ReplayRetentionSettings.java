package gg.modl.backend.settings.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayRetentionSettings {
    private boolean enabled;
    private int days;

    public static ReplayRetentionSettings defaults() {
        return ReplayRetentionSettings.builder()
            .enabled(true)
            .days(7)
            .build();
    }
}
