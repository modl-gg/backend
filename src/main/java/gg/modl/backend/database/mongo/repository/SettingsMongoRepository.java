package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoEntityDiffService;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.settings.data.Settings;
import org.springframework.stereotype.Repository;

@Repository
public class SettingsMongoRepository extends AbstractServerMongoRepository<Settings> {
    public SettingsMongoRepository(TenantMongoAccess tenantMongoAccess, MongoEntityDiffService diffService) {
        super(Settings.class, CollectionName.SETTINGS, diffService, tenantMongoAccess);
    }
}
