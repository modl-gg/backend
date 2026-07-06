package gg.modl.backend.auth;

import gg.modl.backend.auth.data.AuthCode;
import gg.modl.backend.database.mongo.repository.AuthCodeMongoRepository;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.email.EmailHTMLTemplate;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.infrastructure.onetimecode.OneTimeCodeCodec;
import gg.modl.backend.server.data.Server;
import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final EmailService emailService;
    private final AuthCodeMongoRepository authCodeRepository;
    private final AuthConfiguration authConfiguration;
    private final OneTimeCodeCodec oneTimeCodeCodec;

    @Async("emailTaskExecutor")
    public void sendUserLoginCode(Server server, String email) throws MessagingException, UnsupportedEncodingException {
        String code = prepareAndStoreCode(email, (normalizedEmail, codeHash, expiresAt) ->
            authCodeRepository.replaceForServer(server, normalizedEmail, codeHash, expiresAt));

        if (code == null) {
            return;
        }

        emailService.send(email, EmailHTMLTemplate.USER_CODE.build(server.getServerName(), code));
    }

    @Async("emailTaskExecutor")
    public void sendEmailChangeCode(Server server, String newEmail) throws MessagingException, UnsupportedEncodingException {
        String code = prepareAndStoreCode(newEmail, (normalizedEmail, codeHash, expiresAt) ->
            authCodeRepository.replaceForServer(server, normalizedEmail, codeHash, expiresAt));

        if (code == null) {
            return;
        }

        emailService.send(newEmail, EmailHTMLTemplate.EMAIL_CHANGE_CODE.build(server.getServerName(), code));
    }

    private String prepareAndStoreCode(String email, CodeStorageAction storageAction) {
        String code = oneTimeCodeCodec.generateNumericCode(authConfiguration.getEmailCodeLength());
        String codeHash = hash(code);
        String normalizedEmail = EmailAddressUtil.normalize(email);
        Date expiresAt = new Date(System.currentTimeMillis() + (authConfiguration.getEmailCodeExpiry() * 1000L));

        storageAction.store(normalizedEmail, codeHash, expiresAt);

        return code;
    }

    private String hash(String code) {
        return oneTimeCodeCodec.hash(code, authConfiguration.getCodeHashSecret());
    }

    @Async("emailTaskExecutor")
    public void sendAdminLoginCode(String email) throws MessagingException, UnsupportedEncodingException {
        String code = prepareAndStoreCode(email, (normalizedEmail, codeHash, expiresAt) ->
            authCodeRepository.replaceForGlobal(normalizedEmail, codeHash, expiresAt));

        if (code == null) {
            return;
        }

        emailService.send(email, EmailHTMLTemplate.ADMIN_CODE.build(null, code));
    }

    public boolean verifyCode(Server server, String email, String code) {
        String normalizedEmail = EmailAddressUtil.normalize(email);
        String codeHash = hash(code);
        Date now = new Date();

        Optional<AuthCode> consumed = authCodeRepository.consumeIfHashMatchesForServer(server, normalizedEmail, codeHash, now);
        if (consumed.isPresent()) {
            return true;
        }

        authCodeRepository.incrementFailedAttemptsForServer(server, normalizedEmail, now);
        return false;
    }

    public boolean verifyAdminCode(String email, String code) {
        String normalizedEmail = EmailAddressUtil.normalize(email);
        String codeHash = hash(code);
        Date now = new Date();

        Optional<AuthCode> consumed = authCodeRepository.consumeIfHashMatchesForGlobal(normalizedEmail, codeHash, now);
        if (consumed.isPresent()) {
            return true;
        }

        authCodeRepository.incrementFailedAttemptsForGlobal(normalizedEmail, now);
        return false;
    }

    @FunctionalInterface
    private interface CodeStorageAction {
        void store(String normalizedEmail, String codeHash, Date expiresAt);
    }
}
