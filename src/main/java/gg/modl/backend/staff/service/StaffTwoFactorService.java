package gg.modl.backend.staff.service;

import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffTwoFactorService {
    private static final long SESSION_DURATION_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private final StaffMongoRepository staffRepository;

    @Value("${modl.domain:modl.gg}")
    private String modlDomain;

    public Optional<TwoFactorTokenResult> generateToken(Server server, String minecraftUuid, String ip) {
        String token = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();

        if (!staffRepository.createTwoFactorToken(server, minecraftUuid, token, ip, now)) {
            return Optional.empty();
        }

        String domain = server.getCustomDomainOverride();
        if (domain == null || domain.isBlank()) {
            domain = server.getCustomDomain() + "." + modlDomain;
        }

        return Optional.of(new TwoFactorTokenResult(token, "https://" + domain + "/verify/" + token));
    }

    public boolean verifyToken(Server server, String token) {
        Staff staff = staffRepository.findByTwoFactorToken(server, token).orElse(null);
        if (staff == null) {
            return false;
        }

        long now = Instant.now().toEpochMilli();
        String sessionIp = staff.getTwoFactorTokenIp();
        return staffRepository.activateTwoFactorSession(
                server,
                staff.getId(),
                token,
                sessionIp,
                now + SESSION_DURATION_MILLIS
        );
    }

    public record TwoFactorTokenResult(String token, String verifyUrl) {
    }
}
