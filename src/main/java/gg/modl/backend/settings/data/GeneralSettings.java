package gg.modl.backend.settings.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralSettings {
    private String serverDisplayName;
    private String discordWebhookUrl;
    private String homepageIconUrl;
    private String panelIconUrl;

    // New unified label system
    private List<Label> labels;

    // Deprecated: kept for migration compatibility
    @Deprecated
    private List<String> bugReportTags;
    @Deprecated
    private List<String> playerReportTags;
    @Deprecated
    private List<String> appealTags;
}
