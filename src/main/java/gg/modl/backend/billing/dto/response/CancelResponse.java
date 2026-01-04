package gg.modl.backend.billing.dto.response;

import java.util.Date;

public record CancelResponse(
        boolean success,
        String message,
        Date cancelsAt
) {}
