package gg.modl.backend.replay.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "modl.replay.cleanup")
public class LegacyReplayCleanupProperties {
    private boolean enabled = true;

    @Min(60_000)
    private long intervalMs = 3_600_000L;

    @Min(1)
    @Max(500)
    private int batchSize = 100;

    @NotNull
    private Duration claimTtl = Duration.ofMinutes(30);

    public void setClaimTtl(Duration claimTtl) {
        if (claimTtl == null || !claimTtl.isPositive()) {
            throw new IllegalArgumentException("legacy replay cleanup claimTtl must be positive");
        }
        this.claimTtl = claimTtl;
    }
}
