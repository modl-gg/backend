package gg.modl.backend.settings.dto.request;

import org.springframework.lang.Nullable;

public record ApplyAIPunishmentRequest(
    @Nullable
    String staffName
) {}
