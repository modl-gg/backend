package gg.modl.backend.staff.service;

import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.MongoUpdates;
import gg.modl.backend.database.mongo.fields.StaffFields;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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

        Query staffQuery = Query.query(MongoQueries.where(StaffFields.ASSIGNED_MINECRAFT_UUID).is(minecraftUuid));
        Update update = new Update();
        MongoUpdates.set(update, StaffFields.TWO_FACTOR_TOKEN, token);
        MongoUpdates.set(update, StaffFields.TWO_FACTOR_TOKEN_IP, ip);
        MongoUpdates.set(update, StaffFields.TWO_FACTOR_TOKEN_CREATED_AT, now);

        var result = staffRepository.updateFirst(server, staffQuery, update);
        if (result.getMatchedCount() == 0) {
            return Optional.empty();
        }

        String domain = server.getCustomDomainOverride();
        if (domain == null || domain.isBlank()) {
            domain = server.getCustomDomain() + "." + modlDomain;
        }

        return Optional.of(new TwoFactorTokenResult(token, "https://" + domain + "/verify/" + token));
    }

    public boolean verifyToken(Server server, String token) {
        Query query = Query.query(MongoQueries.where(StaffFields.TWO_FACTOR_TOKEN).is(token));
        Staff staff = staffRepository.findOne(server, query).orElse(null);
        if (staff == null) {
            return false;
        }

        Staff original = staffRepository.snapshot(staff);
        long now = Instant.now().toEpochMilli();
        staff.setTwoFactorToken(null);
        staff.setTwoFactorTokenCreatedAt(null);
        staff.setTwoFactorPendingDelivery(true);
        staff.setTwoFactorSessionIp(staff.getTwoFactorTokenIp());
        staff.setTwoFactorTokenIp(null);
        staff.setTwoFactorSessionExpiresAt(now + SESSION_DURATION_MILLIS);
        staffRepository.saveChanges(server, original, staff);
        return true;
    }

    public record TwoFactorTokenResult(String token, String verifyUrl) {
    }
}
