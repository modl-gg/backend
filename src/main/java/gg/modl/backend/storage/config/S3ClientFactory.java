package gg.modl.backend.storage.config;

import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public final class S3ClientFactory {
    private static final Region REGION = Region.US_EAST_1;

    private S3ClientFactory() {
    }

    public static S3Client createClient(String endpoint, String keyId, String applicationKey) {
        return S3Client.builder()
            .credentialsProvider(credentials(keyId, applicationKey))
            .endpointOverride(URI.create(endpoint))
            .region(REGION)
            .forcePathStyle(true)
            .build();
    }

    public static S3Presigner createPresigner(String endpoint, String keyId, String applicationKey) {
        return S3Presigner.builder()
            .credentialsProvider(credentials(keyId, applicationKey))
            .endpointOverride(URI.create(endpoint))
            .region(REGION)
            .build();
    }

    private static StaticCredentialsProvider credentials(String keyId, String applicationKey) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(keyId, applicationKey));
    }
}
