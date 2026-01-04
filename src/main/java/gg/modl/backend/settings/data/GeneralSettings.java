package gg.modl.backend.settings.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralSettings {
    private String serverDisplayName;
    private String discordWebhookUrl;
    private String homepageIconUrl;
    private String panelIconUrl;
}
