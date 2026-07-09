package gg.modl.backend.staff.service;

import gg.modl.backend.infrastructure.config.ModlProperties;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaffTwoFactorService {
    private final StaffMongoRepository staffRepository;
    private final ModlProperties modlProperties;
    private static final long SESSION_DURATION_MILLIS = 7L * 24 * 60 * 60 * 1000;
    private static final long TOKEN_TTL_MILLIS = 10L * 60 * 1000;

    public Optional<TwoFactorTokenResult> generateToken(Server server, String minecraftUuid, String ip) {
        String token = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();

        if (!staffRepository.createTwoFactorToken(server, normalizeUuid(minecraftUuid), token, ip, now)) {
            return Optional.empty();
        }

        String domain = server.getCustomDomainOverride();
        if (domain == null || domain.isBlank()) {
            domain = server.getCustomDomain() + "." + modlProperties.getDomain();
        }

        return Optional.of(new TwoFactorTokenResult(token, "https://" + domain + "/verify/" + token));
    }

    public Optional<VerificationResult> verifyToken(Server server, String token, String sessionEmail) {
        Staff staff = staffRepository.findByTwoFactorToken(server, token).orElse(null);
        if (staff == null) {
            return Optional.empty();
        }

        if (sessionEmail != null && !sessionEmail.isBlank()
            && (staff.getEmail() == null || !staff.getEmail().equalsIgnoreCase(sessionEmail))) {
            return Optional.empty();
        }

        Long tokenCreatedAt = staff.getTwoFactorTokenCreatedAt();
        long now = Instant.now().toEpochMilli();
        if (tokenCreatedAt == null || now - tokenCreatedAt > TOKEN_TTL_MILLIS) {
            return Optional.empty();
        }

        String sessionIp = staff.getTwoFactorTokenIp();
        boolean activated = staffRepository.activateTwoFactorSession(
            server,
            staff.getId(),
            token,
            sessionIp,
            now + SESSION_DURATION_MILLIS
        );
        if (!activated) {
            return Optional.empty();
        }

        String minecraftUuid = staff.getAssignedMinecraftUuid();
        return Optional.of(new VerificationResult(
            minecraftUuid != null && !minecraftUuid.isBlank() ? minecraftUuid : null));
    }

    public record VerificationResult(String minecraftUuid) {
        public Optional<String> minecraftUuidOptional() {
            return Optional.ofNullable(minecraftUuid);
        }
    }

    public record TwoFactorTokenResult(String token, String verifyUrl) {
    }

    private static String normalizeUuid(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
