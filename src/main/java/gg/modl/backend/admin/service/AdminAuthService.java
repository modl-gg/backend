package gg.modl.backend.admin.service;

import gg.modl.backend.admin.data.AdminUser;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.email.EmailHTMLTemplate;
import gg.modl.backend.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthService {
    private static final String COLLECTION = "admin_users";
    private static final long CODE_EXPIRY_MS = 10 * 60 * 1000; // 10 minutes
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DynamicMongoTemplateProvider mongoProvider;
    private final EmailService emailService;

    // In-memory code store (in production, use Redis or database)
    private final Map<String, CodeEntry> codeStore = new ConcurrentHashMap<>();

    private MongoTemplate getTemplate() {
        return mongoProvider.getGlobalDatabase();
    }

    public Optional<AdminUser> findByEmail(String email) {
        String sanitizedEmail = Pattern.quote(email.toLowerCase().trim());
        Query query = Query.query(Criteria.where("email").regex("^" + sanitizedEmail + "$", "i"));
        return Optional.ofNullable(getTemplate().findOne(query, AdminUser.class, COLLECTION));
    }

    public void sendVerificationCode(String email) throws Exception {
        String code = generateCode();
        codeStore.put(email.toLowerCase(), new CodeEntry(code, System.currentTimeMillis() + CODE_EXPIRY_MS));

        EmailHTMLTemplate.HTMLEmail htmlEmail = EmailHTMLTemplate.ADMIN_CODE.build(code, null);
        emailService.send(email, htmlEmail);
        log.info("Sent admin verification code to {}", email);
    }

    public boolean verifyCode(String email, String code) {
        String key = email.toLowerCase();
        CodeEntry entry = codeStore.get(key);

        if (entry == null) {
            return false;
        }

        if (System.currentTimeMillis() > entry.expiresAt()) {
            codeStore.remove(key);
            return false;
        }

        if (MessageDigest.isEqual(entry.code().getBytes(), code.getBytes())) {
            codeStore.remove(key);
            return true;
        }

        return false;
    }

    public void updateLastActivity(String email, String clientIp) {
        String sanitizedEmail = Pattern.quote(email.toLowerCase().trim());
        Query query = Query.query(Criteria.where("email").regex("^" + sanitizedEmail + "$", "i"));
        Update update = new Update()
                .set("lastActivityAt", new Date())
                .addToSet("loggedInIps", clientIp);
        getTemplate().updateFirst(query, update, AdminUser.class, COLLECTION);
    }

    private String generateCode() {
        int code = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    private record CodeEntry(String code, long expiresAt) {}
}
