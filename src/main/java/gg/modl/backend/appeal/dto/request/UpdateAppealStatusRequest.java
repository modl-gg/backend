package gg.modl.backend.appeal.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record UpdateAppealStatusRequest(
    @Pattern(regexp = "(?i)^(open|under[ _-]?review|pending[ _-]?player[ _-]?response|approved|approve|accepted|accept|rejected|reject|dismissed|dismiss|denied|deny)$")
    String status,
    @Nullable Boolean locked,
    @Nullable @Size(max = RequestValidationLimits.APPEAL_STAFF_USERNAME_MAX_LENGTH) String staffUsername,
    @Nullable @Size(max = RequestValidationLimits.APPEAL_RESOLUTION_MAX_LENGTH) String resolution
) {
}
