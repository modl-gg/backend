package gg.modl.backend.settings.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainSettings {
    private String customDomain;
    private DomainStatus status;
    private boolean accessingFromCustomDomain;
    private String modlSubdomainUrl;
    private boolean canManageCustomDomain;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainStatus {
        private String domain;
        @Builder.Default
        private String status = "pending";
        private boolean cnameConfigured;
        @Builder.Default
        private String sslStatus = "pending";
        private String lastChecked;
        private String error;
    }
}
