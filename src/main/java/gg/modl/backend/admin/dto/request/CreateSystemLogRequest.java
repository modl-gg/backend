package gg.modl.backend.admin.dto.request;

import gg.modl.backend.admin.data.SystemLog;
import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateSystemLogRequest(
    @NotBlank
    @Pattern(regexp = "critical|error|warning|info|debug", message = "must be one of: critical, error, warning, info, debug")
    @Size(max = RequestValidationLimits.SYSTEM_LOG_LEVEL_MAX_LENGTH)
    String level,
    @NotBlank
    @Size(max = RequestValidationLimits.SYSTEM_LOG_MESSAGE_MAX_LENGTH)
    String message,
    @NotBlank
    @Size(max = RequestValidationLimits.SYSTEM_LOG_SOURCE_MAX_LENGTH)
    String source,
    @NotBlank
    @Size(max = RequestValidationLimits.SYSTEM_LOG_CATEGORY_MAX_LENGTH)
    String category,
    @NotBlank
    @Size(max = RequestValidationLimits.LOG_SERVER_NAME_MAX_LENGTH)
    String serverId,
    @Size(max = RequestValidationLimits.SYSTEM_LOG_METADATA_MAX_ENTRIES)
    Map<
        @NotBlank @Size(max = RequestValidationLimits.SYSTEM_LOG_METADATA_KEY_MAX_LENGTH) String,
        Object
        > metadata
) {
    public SystemLog toSystemLog() {
        SystemLog log = new SystemLog();
        log.setLevel(level);
        log.setMessage(message);
        log.setSource(source);
        log.setCategory(category);
        log.setServerId(serverId);
        log.setMetadata(metadata);
        return log;
    }
}
