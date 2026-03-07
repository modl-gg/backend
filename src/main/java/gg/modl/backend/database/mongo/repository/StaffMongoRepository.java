package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoEntityDiffService;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.staff.data.Staff;
import org.springframework.stereotype.Repository;

@Repository
public class StaffMongoRepository extends AbstractServerMongoRepository<Staff> {
    public StaffMongoRepository(TenantMongoAccess tenantMongoAccess, MongoEntityDiffService diffService) {
        super(Staff.class, CollectionName.STAFF, diffService, tenantMongoAccess);
    }
}
