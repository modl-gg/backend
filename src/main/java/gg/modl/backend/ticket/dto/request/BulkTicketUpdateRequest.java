package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.lang.Nullable;

public record BulkTicketUpdateRequest(
    @NotEmpty
    @Size(max = RequestValidationLimits.TICKET_IDS_MAX_ENTRIES)
    List<@NotBlank @Size(max = RequestValidationLimits.NOTIFICATION_ID_MAX_LENGTH) String> ticketIds,
    @Nullable Boolean locked,
    @Nullable @Size(max = RequestValidationLimits.TICKET_LABELS_MAX_ENTRIES) List<@Size(max = RequestValidationLimits.TICKET_TAG_MAX_LENGTH) String> addLabels,
    @Nullable @Size(max = RequestValidationLimits.TICKET_LABELS_MAX_ENTRIES) List<@Size(max = RequestValidationLimits.TICKET_TAG_MAX_LENGTH) String> removeLabels,
    @Nullable @Size(max = RequestValidationLimits.TICKET_ASSIGNEE_MAX_LENGTH) String assignTo,
    @Nullable Boolean hidden
) {
}
