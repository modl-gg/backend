package gg.modl.backend.replaylite.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "modl.replay-lite.storage")
@Getter
@Setter
public class ReplayLiteStorageConfiguration {
    private String keyId = "";
    private String applicationKey = "";
    private String endpoint = "";
    private String bucketName = "";
    private String cdnDomain = "";

    boolean isConfigured() {
        return hasCredentials() && bucketName != null && !bucketName.isBlank();
    }

    private boolean hasCredentials() {
        return keyId != null && !keyId.isBlank()
            && applicationKey != null && !applicationKey.isBlank()
            && endpoint != null && !endpoint.isBlank();
    }
}
