package gg.modl.backend.player.dto.request;

import org.springframework.lang.Nullable;

public record CreateUploadTokenRequest(
        @Nullable String issuerName,
        @Nullable String issuerId
) {}
