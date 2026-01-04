package gg.modl.backend.storage.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Configuration {

    @Value("${modl.storage.key-id:}")
    private String keyId;

    @Value("${modl.storage.application-key:}")
    private String applicationKey;

    @Value("${modl.storage.endpoint:}")
    private String endpoint;

    @Bean
    public S3Client s3Client() {
        if (keyId.isBlank() || applicationKey.isBlank() || endpoint.isBlank()) {
            return null;
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(keyId, applicationKey);

        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        if (keyId.isBlank() || applicationKey.isBlank() || endpoint.isBlank()) {
            return null;
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(keyId, applicationKey);

        return S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .build();
    }
}
