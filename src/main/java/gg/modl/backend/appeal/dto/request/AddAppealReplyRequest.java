package gg.modl.backend.appeal.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AddAppealReplyRequest(
        @NotBlank String name,
        @NotBlank String content,
        @NotBlank String type,
        boolean staff,
        String action,
        String avatar,
        List<Object> attachments
) {
}
