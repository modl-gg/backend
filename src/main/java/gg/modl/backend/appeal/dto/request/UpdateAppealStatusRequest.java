package gg.modl.backend.appeal.dto.request;

import jakarta.validation.constraints.Pattern;

public record UpdateAppealStatusRequest(
        @Pattern(regexp = "(?i)^(open|under[ _-]?review|pending[ _-]?player[ _-]?response|approved|approve|accepted|accept|rejected|reject|dismissed|dismiss|denied|deny)$")
        String status,
        Boolean locked,
        String staffUsername,
        String resolution
) {
}
