package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.server.data.CustomDomainStatus;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class ServerCustomDomainRepository extends AbstractGlobalMongoRepository<Server> {

    public ServerCustomDomainRepository(TenantMongoAccess tenantMongoAccess) {
        super(Server.class, CollectionName.MODL_SERVERS, tenantMongoAccess);
    }

    public void updateCustomDomain(String serverId, String customDomain, CustomDomainStatus status,
                                   String cloudflareHostnameId, String error) {
        updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            new Update()
                .set(ServerFields.CUSTOM_DOMAIN_OVERRIDE, customDomain)
                .set(ServerFields.CUSTOM_DOMAIN_STATUS, status.name())
                .set(ServerFields.CUSTOM_DOMAIN_CLOUDFLARE_ID, cloudflareHostnameId)
                .set(ServerFields.CUSTOM_DOMAIN_LAST_CHECKED, new Date())
                .set(ServerFields.CUSTOM_DOMAIN_ERROR, error)
                .set(ServerFields.UPDATED_AT, new Date())
        );
    }

    public void clearCustomDomain(String serverId) {
        updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            new Update()
                .unset(ServerFields.CUSTOM_DOMAIN_OVERRIDE)
                .unset(ServerFields.CUSTOM_DOMAIN_STATUS)
                .unset(ServerFields.CUSTOM_DOMAIN_CLOUDFLARE_ID)
                .unset(ServerFields.CUSTOM_DOMAIN_LAST_CHECKED)
                .unset(ServerFields.CUSTOM_DOMAIN_ERROR)
                .set(ServerFields.UPDATED_AT, new Date())
        );
    }
}
