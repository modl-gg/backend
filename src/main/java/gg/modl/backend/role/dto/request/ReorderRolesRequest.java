package gg.modl.backend.role.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReorderRolesRequest(
    @NotNull
    @Size(max = RequestValidationLimits.ROLE_ORDER_MAX_ENTRIES)
    List<@Valid RoleOrderItem> roleOrder
) {
    public record RoleOrderItem(
        @NotBlank @Size(max = RequestValidationLimits.NOTIFICATION_ID_MAX_LENGTH) String id,
        @PositiveOrZero int order
    ) {
    }
}
