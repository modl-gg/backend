package gg.modl.backend.storage.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitEvidenceRequest(
        @NotEmpty
        @Size(max = RequestValidationLimits.EVIDENCE_MAX_ITEMS)
        List<@Valid EvidenceItemRequest> evidence
) {
}