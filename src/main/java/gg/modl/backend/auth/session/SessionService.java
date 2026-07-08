package gg.modl.backend.auth.session;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.database.mongo.repository.AuthSessionMongoRepository;
import gg.modl.backend.infrastructure.util.IdGenerator;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final AuthSessionMongoRepository sessionRepository;
    private final AuthConfiguration authConfiguration;
    private final IdGenerator idGenerator;

    public AuthSessionData createSession(Server server, String email, String ipAddress, String userAgent) {
        return createSessionInternal(email, ipAddress, userAgent, session -> sessionRepository.saveForServer(server, session));
    }

    private AuthSessionData createSessionInternal(
        String email,
        String ipAddress,
        String userAgent,
        Function<AuthSessionData, AuthSessionData> saver
    ) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Session email cannot be empty");
        }

        AuthSessionData session = new AuthSessionData();
        session.setId(generateSecureToken());
        session.setEmail(EmailAddressUtil.normalize(email));
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);

        Date now = new Date();
        session.setCreatedAt(now);
        session.setExpiresAt(nextExpiryDate(now));
        return saver.apply(session);
    }

    private String generateSecureToken() {
        return idGenerator.generateToken();
    }

    private Date nextExpiryDate(Date now) {
        return new Date(now.getTime() + (authConfiguration.getSessionDurationSeconds() * 1000));
    }

    public AuthSessionData createAdminSession(String email) {
        return createSessionInternal(email, null, null, sessionRepository::saveForGlobal);
    }

    public List<AuthSessionData> findAllSessionsForEmail(Server server, String email) {
        return sessionRepository.findActiveByEmail(server, email, new Date());
    }

    public Optional<AuthSessionData> findSessionByPublicId(Server server, String email, String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return findAllSessionsForEmail(server, email).stream()
            .filter(session -> publicId.equals(SessionPublicId.of(session.getId())))
            .findFirst();
    }

    private static final long REFRESH_SKIP_THRESHOLD_MS = 10L * 60 * 1000;

    public Optional<AuthSessionData> findAndRefreshSession(Server server, String sessionId) {
        return findAndRefreshInternal(
            sessionId,
            now -> sessionRepository.findActiveById(server, sessionId, now),
            (now, newExpiresAt) -> sessionRepository.findAndRefreshById(server, sessionId, now, newExpiresAt)
        );
    }

    public Optional<AuthSessionData> findValidSession(Server server, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return sessionRepository.findActiveById(server, sessionId, new Date());
    }

    public void refreshSession(Server server, String sessionId) {
        sessionRepository.refreshExpiresAt(server, sessionId, nextExpiryDate());
    }

    private Date nextExpiryDate() {
        return nextExpiryDate(new Date());
    }

    public Optional<AuthSessionData> findAndRefreshAdminSession(String sessionId) {
        return findAndRefreshInternal(
            sessionId,
            now -> sessionRepository.findActiveByIdGlobal(sessionId, now),
            (now, newExpiresAt) -> sessionRepository.findAndRefreshByIdGlobal(sessionId, now, newExpiresAt)
        );
    }

    private Optional<AuthSessionData> findAndRefreshInternal(
        String sessionId,
        Function<Date, Optional<AuthSessionData>> findActive,
        BiFunction<Date, Date, Optional<AuthSessionData>> findAndRefresh
    ) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        Date now = new Date();
        Date refreshThreshold = new Date(now.getTime() + (authConfiguration.getSessionDurationSeconds() * 1000) - REFRESH_SKIP_THRESHOLD_MS);
        Optional<AuthSessionData> sessionOpt = findActive.apply(now);
        if (sessionOpt.isPresent() && sessionOpt.get().getExpiresAt().before(refreshThreshold)) {
            return findAndRefresh.apply(now, nextExpiryDate(now));
        }
        return sessionOpt;
    }

    public Optional<AuthSessionData> findValidAdminSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return sessionRepository.findActiveByIdGlobal(sessionId, new Date());
    }

    public void refreshAdminSession(String sessionId) {
        sessionRepository.refreshExpiresAtGlobal(sessionId, nextExpiryDate());
    }

    public void invalidateSession(Server server, String sessionId) {
        sessionRepository.deleteById(server, sessionId);
    }

    public void invalidateAdminSession(String sessionId) {
        sessionRepository.deleteByIdGlobal(sessionId);
    }

    public void invalidateAllSessionsForEmail(Server server, String email) {
        sessionRepository.deleteByEmail(server, email);
    }

    public void invalidateAllAdminSessionsForEmail(String email) {
        sessionRepository.deleteByEmailGlobal(email);
    }
}
