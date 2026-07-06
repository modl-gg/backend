package gg.modl.backend.infrastructure.onetimecode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class OneTimeCodeCodec {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DIGEST_ALGORITHM = "SHA-256";

    public String generateNumericCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(SECURE_RANDOM.nextInt(10));
        }
        return code.toString();
    }

    public String hash(String code, String secret) {
        try {
            byte[] input = code.getBytes(StandardCharsets.UTF_8);
            if (secret != null && !secret.isBlank()) {
                Mac mac = Mac.getInstance(HMAC_ALGORITHM);
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
                return Base64.getEncoder().encodeToString(mac.doFinal(input));
            }
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return Base64.getEncoder().encodeToString(digest.digest(input));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash one-time code", e);
        }
    }
}
