package gg.modl.backend.storage.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.storage.data.EvidenceUploadTokenDocument;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class EvidenceUploadTokenMongoRepository extends AbstractGlobalMongoRepository<EvidenceUploadTokenDocument> {

    public EvidenceUploadTokenMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(EvidenceUploadTokenDocument.class, CollectionName.EVIDENCE_UPLOAD_TOKENS, tenantMongoAccess);
    }

    public Optional<EvidenceUploadTokenDocument> findByTokenHash(String hash) {
        return findById(hash);
    }

    public void deleteByTokenHash(String hash) {
        remove(Query.query(Criteria.where("_id").is(hash)));
    }
}
