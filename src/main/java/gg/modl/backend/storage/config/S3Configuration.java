package gg.modl.backend.storage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConfigurationProperties(prefix = "modl.storage")
@Validated
@Getter
@Setter
public class S3Configuration {
    private String keyId = "";
    private String applicationKey = "";
    private String endpoint = "";
    private String bucketName = "";
    private String cdnDomain = "";

    @Bean
    public S3Client s3Client() {
        if (keyId.isBlank() || applicationKey.isBlank() || endpoint.isBlank()) {
            return null;
        }

        return S3ClientFactory.createClient(endpoint, keyId, applicationKey);
    }

    @Bean
    public S3Presigner s3Presigner() {
        if (keyId.isBlank() || applicationKey.isBlank() || endpoint.isBlank()) {
            return null;
        }

        return S3ClientFactory.createPresigner(endpoint, keyId, applicationKey);
    }
}
