package gg.modl.backend.auth;

import gg.modl.backend.auth.data.AuthCode;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.email.EmailHTMLTemplate;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.server.data.Server;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailService emailService;
    private final DynamicMongoTemplateProvider mongoProvider;
    private final AuthConfiguration authConfiguration;

    public void sendUserLoginCode(Server server, String email) throws MessagingException, UnsupportedEncodingException {
        String code = generateNumericCode(authConfiguration.getEmailCodeLength());
        String codeHash = hashCode(code);
        String normalizedEmail = email.toLowerCase();

        MongoTemplate mongo = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query existingQuery = new Query(Criteria.where("email").is(normalizedEmail));
        mongo.remove(existingQuery, AuthCode.class);

        AuthCode authCode = new AuthCode();
        authCode.setEmail(normalizedEmail);
        authCode.setCodeHash(codeHash);
        authCode.setExpiresAt(Instant.now().plusSeconds(authConfiguration.getEmailCodeExpiry()));

        mongo.save(authCode);

        EmailHTMLTemplate.HTMLEmail emailContent = EmailHTMLTemplate.USER_CODE.build(server.getServerName(), code);
        emailService.send(email, emailContent);
    }

    public void sendAdminLoginCode(String email) throws MessagingException, UnsupportedEncodingException {
        String code = generateNumericCode(authConfiguration.getEmailCodeLength());
        String codeHash = hashCode(code);
        String normalizedEmail = email.toLowerCase();

        MongoTemplate mongo = mongoProvider.getGlobalDatabase();

        Query existingQuery = new Query(Criteria.where("email").is(normalizedEmail));
        mongo.remove(existingQuery, AuthCode.class);

        AuthCode authCode = new AuthCode();
        authCode.setEmail(normalizedEmail);
        authCode.setCodeHash(codeHash);
        authCode.setExpiresAt(Instant.now().plusSeconds(authConfiguration.getEmailCodeExpiry()));

        mongo.save(authCode);

        EmailHTMLTemplate.HTMLEmail emailContent = EmailHTMLTemplate.ADMIN_CODE.build(code, null);
        emailService.send(email, emailContent);
    }

    public boolean verifyCode(Server server, String email, String code) {
        MongoTemplate mongo = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        return verifyCodeInternal(mongo, email, code);
    }

    public boolean verifyAdminCode(String email, String code) {
        MongoTemplate mongo = mongoProvider.getGlobalDatabase();
        return verifyCodeInternal(mongo, email, code);
    }

    private boolean verifyCodeInternal(MongoTemplate mongo, String email, String code) {
        String normalizedEmail = email.toLowerCase();

        Query query = new Query(Criteria.where("email").is(normalizedEmail)
                .and("expiresAt").gt(Instant.now()));

        AuthCode authCode = mongo.findOne(query, AuthCode.class);

        if (authCode == null) {
            return false;
        }

        String providedHash = hashCode(code);
        boolean valid = MessageDigest.isEqual(
                providedHash.getBytes(StandardCharsets.UTF_8),
                authCode.getCodeHash().getBytes(StandardCharsets.UTF_8)
        );

        if (valid) {
            mongo.remove(authCode);
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
