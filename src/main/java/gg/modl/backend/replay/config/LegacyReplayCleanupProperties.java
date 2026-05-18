package gg.modl.backend.replay.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "modl.replay.cleanup")
public class LegacyReplayCleanupProperties {
    private boolean enabled = true;

    @Min(60_000)
    private long intervalMs = 3_600_000L;

    @Min(1)
    @Max(500)
    private int batchSize = 100;
}
