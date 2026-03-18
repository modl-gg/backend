package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MinecraftTicketsByIdsRequest(
    @NotEmpty
    @Size(max = RequestValidationLimits.TICKET_IDS_MAX_ENTRIES)
    List<@NotBlank @Size(max = RequestValidationLimits.NOTIFICATION_ID_MAX_LENGTH) String> ids
) {
}
