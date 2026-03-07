package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoEntityDiffService;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.role.data.StaffRole;
import org.springframework.stereotype.Repository;

@Repository
public class StaffRoleMongoRepository extends AbstractServerMongoRepository<StaffRole> {
    public StaffRoleMongoRepository(TenantMongoAccess tenantMongoAccess, MongoEntityDiffService diffService) {
        super(StaffRole.class, CollectionName.STAFF_ROLES, diffService, tenantMongoAccess);
    }
}
