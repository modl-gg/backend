package gg.modl.backend.player.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public record CreatePunishmentRequest(
    @Nullable String issuerName,
    @Nullable String issuerId,
    @NotNull Integer typeOrdinal,
    @Nullable List<CreateNoteRequest> notes,
    @Nullable List<CreateEvidenceRequest> evidence,
    @Nullable List<String> attachedTicketIds,
    @Nullable String severity,
    @Nullable String status,
    @Nullable Map<String, Object> data,
    @Nullable String reason,
    @Nullable Long duration
) {
}
