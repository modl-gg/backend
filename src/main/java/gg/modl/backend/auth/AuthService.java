package gg.modl.backend.auth;

import gg.modl.backend.auth.data.AuthCode;
import gg.modl.backend.database.mongo.repository.AuthCodeMongoRepository;
import gg.modl.backend.email.EmailHTMLTemplate;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.server.data.Server;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final EmailService emailService;
    private final AuthCodeMongoRepository authCodeRepository;
    private final AuthConfiguration authConfiguration;

    public void sendUserLoginCode(Server server, String email) throws MessagingException, UnsupportedEncodingException {
        String code = prepareAndStoreCode(email, (normalizedEmail, codeHash, expiresAt) ->
                authCodeRepository.replaceForServer(server, normalizedEmail, codeHash, expiresAt));

        if (code == null) return;

        emailService.send(email, EmailHTMLTemplate.USER_CODE.build(server.getServerName(), code));
    }

    public void sendAdminLoginCode(String email) throws MessagingException, UnsupportedEncodingException {
        String code = prepareAndStoreCode(email, (normalizedEmail, codeHash, expiresAt) ->
                authCodeRepository.replaceForGlobal(normalizedEmail, codeHash, expiresAt));

        if (code == null) return;

        emailService.send(email, EmailHTMLTemplate.ADMIN_CODE.build(code, null));
    }

    private String prepareAndStoreCode(String email, CodeStorageAction storageAction) {
        String code = generateNumericCode(authConfiguration.getEmailCodeLength());
        String codeHash = hashCode(code);
        String normalizedEmail = email.toLowerCase();
        Date expiresAt = new Date(System.currentTimeMillis() + (authConfiguration.getEmailCodeExpiry() * 1000L));

        storageAction.store(normalizedEmail, codeHash, expiresAt);

        if (authConfiguration.isDevelopmentMode()) {
            log.info("DEV MODE: Login code for {} is: {}", email, code);
            return null;
        }

        return code;
    }

    @FunctionalInterface
    private interface CodeStorageAction {
        void store(String normalizedEmail, String codeHash, Date expiresAt);
    }

    public boolean verifyCode(Server server, String email, String code) {
        String normalizedEmail = email.toLowerCase();
        Date now = new Date();
        return verifyCodeInternal(
                code,
                authCodeRepository.findActiveForServer(server, normalizedEmail, now),
                () -> authCodeRepository.deleteForServer(server, normalizedEmail),
                () -> authCodeRepository.incrementFailedAttemptsForServer(server, normalizedEmail, now)
        );
    }

    public boolean verifyAdminCode(String email, String code) {
        String normalizedEmail = email.toLowerCase();
        Date now = new Date();
        return verifyCodeInternal(
                code,
                authCodeRepository.findActiveForGlobal(normalizedEmail, now),
                () -> authCodeRepository.deleteForGlobal(normalizedEmail),
                () -> authCodeRepository.incrementFailedAttemptsForGlobal(normalizedEmail, now)
        );
    }

    private boolean verifyCodeInternal(
            String code,
            Optional<AuthCode> authCodeOpt,
            Runnable onDelete,
            Runnable onFailedAttempt
    ) {
        if (authCodeOpt.isEmpty()) {
            return false;
        }

        AuthCode authCode = authCodeOpt.get();
        if (authCode.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            onDelete.run();
            return false;
        }

        String providedHash = hashCode(code);
        boolean valid = MessageDigest.isEqual(
                providedHash.getBytes(StandardCharsets.UTF_8),
                authCode.getCodeHash().getBytes(StandardCharsets.UTF_8)
        );

        if (valid) {
            onDelete.run();
        } else {
            onFailedAttempt.run();
        }

        return valid;
    }

    private String generateNumericCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(SECURE_RANDOM.nextInt(10));
        }
        return code.toString();
    }

    private String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
