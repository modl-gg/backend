package gg.modl.backend.appeal.dto.request;

public record UpdateAppealStatusRequest(
        String status,
        Boolean locked,
        String staffUsername,
        String resolution
) {
}
