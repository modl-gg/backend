package gg.modl.backend.player.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatLogBatchRequest(
    @NotEmpty
    @Size(max = RequestValidationLimits.CHAT_LOG_BATCH_MAX_ENTRIES)
    List<@Valid ChatLogEntryRequest> entries
) {
}