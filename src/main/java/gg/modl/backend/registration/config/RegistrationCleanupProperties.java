package gg.modl.backend.registration.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Validated
@ConfigurationProperties(prefix = "modl.registration.cleanup")
public class RegistrationCleanupProperties {
    @Setter
    private boolean enabled = true;

    @Setter
    private boolean dryRun = false;

    @NotNull
    private Duration expiry = Duration.ofHours(24);

    @Min(60_000)
    @Setter
    private long intervalMs = 3_600_000L;

    @Min(1)
    @Max(1_000)
    @Setter
    private int batchSize = 100;

    @NotNull
    private Duration claimTtl = Duration.ofHours(6);

    public void setExpiry(Duration expiry) {
        if (expiry == null || !expiry.isPositive()) {
            throw new IllegalArgumentException("registration cleanup expiry must be positive");
        }
        this.expiry = expiry;
    }

    public void setClaimTtl(Duration claimTtl) {
        if (claimTtl == null || !claimTtl.isPositive()) {
            throw new IllegalArgumentException("registration cleanup claimTtl must be positive");
        }
        this.claimTtl = claimTtl;
    }
}
