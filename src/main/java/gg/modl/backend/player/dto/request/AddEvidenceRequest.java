package gg.modl.backend.player.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.Nullable;

public record AddEvidenceRequest(
        @Nullable String text,
        @NotBlank String type,
        @Nullable String issuerName,
        @Nullable String url,
        @Nullable String fileName,
        @Nullable String fileType,
        @Nullable Long fileSize
) {
}
