package gg.modl.backend.player.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.Nullable;

public record CreateEvidenceRequest(
        @NotBlank String text,
        @Nullable String issuerName,
        @Nullable String date,
        @Nullable String type,
        @Nullable String fileUrl,
        @Nullable String fileName,
        @Nullable String fileType,
        @Nullable Long fileSize
) {
}
