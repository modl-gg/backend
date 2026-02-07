package gg.modl.backend.ai.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIAnalysisResult {
    private String analysis;
    private SuggestedAction suggestedAction;
    private boolean wasAppliedAutomatically;
    private boolean dismissed;
    private Date createdAt;
    private String rawResponse;

    public boolean hasViolation() {
        return suggestedAction != null && suggestedAction.getPunishmentTypeId() != null;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestedAction {
        private Integer punishmentTypeId;
        private String severity;
    }
}
