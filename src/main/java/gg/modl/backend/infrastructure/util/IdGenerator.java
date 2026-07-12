package gg.modl.backend.infrastructure.util;

import gg.modl.backend.infrastructure.rest.RequestUtil;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class IdGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    public int nextSixDigitInt() {
        return 100000 + RANDOM.nextInt(900000);
    }

    public String generateToken() {
        return RequestUtil.generateSecureToken(32);
    }

    public static String generateShortId() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append((char) ('A' + RANDOM.nextInt(26)));
        }
        return sb.toString();
    }
}
