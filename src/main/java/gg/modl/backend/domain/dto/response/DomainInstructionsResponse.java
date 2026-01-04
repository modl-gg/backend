package gg.modl.backend.domain.dto.response;

import java.util.List;

public record DomainInstructionsResponse(
        String cnameTarget,
        List<InstructionStep> steps
) {
    public record InstructionStep(
            int step,
            String title,
            String description
    ) {}
}
