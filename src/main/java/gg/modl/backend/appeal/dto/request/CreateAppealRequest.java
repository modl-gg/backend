package gg.modl.backend.appeal.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record CreateAppealRequest(
        @NotBlank String punishmentId,
        @NotBlank String playerUuid,
        @NotBlank @Email String email,
        String reason,
        String evidence,
        Map<String, Object> additionalData,
        List<Object> attachments,
        Map<String, String> fieldLabels
) {
}
