package gg.modl.backend.staff.dto.response;

import java.util.List;

public record InviteResultResponse(
        String message,
        List<String> success,
        List<FailedInvite> failed
) {
    public record FailedInvite(
            String email,
            String reason
    ) {
    }
}
