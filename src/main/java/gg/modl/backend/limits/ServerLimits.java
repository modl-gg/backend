package gg.modl.backend.limits;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ServerLimits {
    long maxStaffSeats;
    long maxStorageBytes;
    boolean aiModerationEnabled;
    long aiRequestLimit;
    boolean customDomainAllowed;
    long migrationFileSizeLimit;
    long maxUploadBytes;

    public boolean exceedsUploadLimit(long size) {
        return size > maxUploadBytes;
    }
}
