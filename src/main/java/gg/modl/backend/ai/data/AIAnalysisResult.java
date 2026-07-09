package gg.modl.backend.ai.data;

import java.util.Date;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class AIAnalysisResult {
    @NotNull
    private final String analysis;
    @Nullable
    private final SuggestedAction suggestedAction;
    @NotNull
    private final Date createdAt;
    @NotNull
    private final String rawResponse;
    @Nullable
    private Double confidence;
    private boolean wasAppliedAutomatically;
    private boolean dismissed = false;

    public boolean hasViolation() {
        return suggestedAction != null;
    }

    @Data
    public static class SuggestedAction {
        private final int punishmentTypeId;
        @NotNull
        private final String severity;
    }
}
