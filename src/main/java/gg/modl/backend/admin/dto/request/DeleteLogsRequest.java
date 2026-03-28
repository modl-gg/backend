package gg.modl.backend.admin.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DeleteLogsRequest(
    @NotEmpty
    @Size(max = RequestValidationLimits.DELETE_LOGS_MAX_IDS)
    List<@NotBlank @Size(max = RequestValidationLimits.ID_MAX_LENGTH) String> logIds
) {}
