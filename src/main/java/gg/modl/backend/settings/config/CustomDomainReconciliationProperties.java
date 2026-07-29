package gg.modl.backend.settings.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "modl.custom-domain.reconciliation")
public class CustomDomainReconciliationProperties {
    private boolean enabled = true;

    private boolean orphanGarbageCollection = true;

    @Min(60_000)
    private long intervalMs = 21_600_000L;
}
