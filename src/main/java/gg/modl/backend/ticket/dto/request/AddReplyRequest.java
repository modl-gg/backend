package gg.modl.backend.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AddReplyRequest(
        @NotBlank String name,
        @NotBlank String content,
        String type,
        boolean staff,
        String avatar,
        List<Object> attachments,
        String action,
        String creatorIdentifier
) {
}
