package gg.modl.backend.infrastructure.proto;

import build.buf.protovalidate.ValidationResult;
import build.buf.validate.Violation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.stream.Collectors;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ProtoValidationException extends RuntimeException {

    private final List<Violation> violations;

    public ProtoValidationException(ValidationResult result) {
        super(formatViolations(result.getViolations()));
        this.violations = List.copyOf(result.getViolations());
    }

    public List<Violation> getViolations() {
        return violations;
    }

    private static String formatViolations(List<Violation> violationList) {
        return violationList.stream()
                .map(violation -> violation.getFieldPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
    }
}
