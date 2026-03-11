package gg.modl.backend.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class IdGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    public int nextSixDigitInt() {
        return 100000 + RANDOM.nextInt(900000);
    }

    public String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
