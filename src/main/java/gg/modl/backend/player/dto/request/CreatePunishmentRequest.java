package gg.modl.backend.player.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record CreatePunishmentRequest(
        @NotBlank String issuerName,
        @NotNull Integer typeOrdinal,
        @Nullable List<String> notes,
        @Nullable List<String> evidence,
        @Nullable List<String> attachedTicketIds,
        @Nullable String severity,
        @Nullable String status,
        @Nullable Map<String, Object> data
) {
}
