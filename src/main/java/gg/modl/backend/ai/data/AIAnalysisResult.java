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
    private boolean violationDetected;
    private String violationType;
    private String severity;
    private String recommendedAction;
    private String explanation;
    private Double confidence;
    private boolean actionTaken;
    private String actionDetails;
    private String punishmentId;
    private Date analyzedAt;
    private String rawResponse;
}
