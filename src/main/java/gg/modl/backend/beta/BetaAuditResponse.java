package gg.modl.backend.beta;

import java.time.Instant;
import java.util.List;

public record BetaAuditResponse(List<Entry> entries) {
    public record Entry(String action, String adminEmail, Instant timestamp, String details) {
    }
}
