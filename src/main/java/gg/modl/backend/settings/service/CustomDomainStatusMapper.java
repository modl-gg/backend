package gg.modl.backend.settings.service;

import gg.modl.backend.cloudflare.external.CloudflareClient;
import gg.modl.backend.server.data.CustomDomainStatus;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class CustomDomainStatusMapper {
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_ERROR = "error";
    private static final String CF_BLOCKED = "blocked";
    private static final String CF_MOVED = "moved";
    private static final String HOSTNAME_MISSING_ERROR =
        "Custom hostname not found in Cloudflare. Please reconfigure the domain.";

    public Resolution resolve(@Nullable CloudflareClient.CustomHostnameResult cfResult) {
        if (cfResult == null) {
            return new Resolution(STATUS_ERROR, STATUS_ERROR, false, HOSTNAME_MISSING_ERROR);
        }

        String normalizedCfStatus = normalize(cfResult.status());
        String status = mapStatus(normalizedCfStatus);
        boolean cnameConfigured = STATUS_ACTIVE.equals(status);
        String sslStatus = cfResult.ssl() != null ? mapStatus(normalize(cfResult.ssl().status())) : STATUS_PENDING;
        String error = isRejected(normalizedCfStatus)
                       ? "Domain verification failed. Status: " + normalizedCfStatus
                       : null;
        return new Resolution(status, sslStatus, cnameConfigured, error);
    }

    public CustomDomainStatus toEnum(String status) {
        return switch (status) {
            case STATUS_ACTIVE -> CustomDomainStatus.ACTIVE;
            case STATUS_ERROR -> CustomDomainStatus.ERROR;
            default -> CustomDomainStatus.PENDING;
        };
    }

    private String normalize(@Nullable String cfStatus) {
        return cfStatus == null ? null : cfStatus.toLowerCase(Locale.ROOT);
    }

    private String mapStatus(@Nullable String normalizedCfStatus) {
        if (normalizedCfStatus == null) {
            return STATUS_PENDING;
        }
        return switch (normalizedCfStatus) {
            case STATUS_ACTIVE -> STATUS_ACTIVE;
            case CF_BLOCKED, CF_MOVED -> STATUS_ERROR;
            default -> STATUS_PENDING;
        };
    }

    private boolean isRejected(@Nullable String normalizedCfStatus) {
        return CF_BLOCKED.equals(normalizedCfStatus) || CF_MOVED.equals(normalizedCfStatus);
    }

    public record Resolution(String status, String sslStatus, boolean cnameConfigured, String error) {}
}
