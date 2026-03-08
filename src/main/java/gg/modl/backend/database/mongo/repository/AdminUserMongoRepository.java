package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.admin.data.AdminUser;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.AdminUserFields;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;
import java.util.regex.Pattern;

@Repository
public class AdminUserMongoRepository extends AbstractGlobalMongoRepository<AdminUser> {
    private static final String COLLECTION_NAME = "admin_users";

    public AdminUserMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(AdminUser.class, COLLECTION_NAME, tenantMongoAccess);
    }

    public Optional<AdminUser> findByEmailIgnoreCase(String email) {
        String normalizedEmail = email == null ? "" : email.toLowerCase().trim();
        String escapedEmail = Pattern.quote(normalizedEmail);
        Query query = Query.query(MongoQueries.where(AdminUserFields.EMAIL).regex("^" + escapedEmail + "$", "i"));
        return findOne(query);
    }

    public void updateLastActivity(String email, String clientIp, Date lastActivityAt) {
        String normalizedEmail = email == null ? "" : email.toLowerCase().trim();
        String escapedEmail = Pattern.quote(normalizedEmail);
        Query query = Query.query(MongoQueries.where(AdminUserFields.EMAIL).regex("^" + escapedEmail + "$", "i"));
        Update update = new Update()
                .set(AdminUserFields.LAST_ACTIVITY_AT, lastActivityAt)
                .addToSet(AdminUserFields.LOGGED_IN_IPS, clientIp);
        updateFirst(query, update);
    }
}
