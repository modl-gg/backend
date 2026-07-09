package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.StorageFileDocumentFields;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.data.StorageFileDocument;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class StorageFileMongoRepository extends AbstractServerMongoRepository<StorageFileDocument> {

    public StorageFileMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(StorageFileDocument.class, CollectionName.STORAGE_FILES, tenantMongoAccess);
    }

    public Optional<StorageFileDocument> findByKey(Server server, String key) {
        return findOne(server, Query.query(Criteria.where(StorageFileDocumentFields.KEY).is(key)));
    }

    public List<StorageFileDocument> findByKeyPrefix(Server server, String prefix, int limit) {
        Query query = Query.query(Criteria.where(StorageFileDocumentFields.KEY).regex("^" + escapeRegex(prefix)))
            .with(Sort.by(Sort.Direction.DESC, StorageFileDocumentFields.CREATED_AT))
            .limit(limit);
        return find(server, query);
    }

    public Optional<StorageFileDocument> findAndRemoveByKey(Server server, String key) {
        return Optional.ofNullable(findAndRemove(server, Query.query(Criteria.where(StorageFileDocumentFields.KEY).is(key))));
    }

    public List<StorageFileDocument> findAndRemoveByKeys(Server server, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<StorageFileDocument> removed = new ArrayList<>(keys.size());
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            findAndRemoveByKey(server, key).ifPresent(removed::add);
        }
        return removed;
    }

    public List<StorageFileDocument> findByKeys(Server server, List<String> keys) {
        return find(server, Query.query(Criteria.where(StorageFileDocumentFields.KEY).in(keys)));
    }

    public void deleteByKeyNotIn(Server server, List<String> keys) {
        // $nin against an empty array matches EVERY document, which would wipe the whole
        // collection. With no authoritative keep-set there is nothing to prune, so no-op.
        if (keys == null || keys.isEmpty()) {
            return;
        }
        remove(server, Query.query(Criteria.where(StorageFileDocumentFields.KEY).nin(keys)));
    }

    public long sumSizeByKeyPrefixesSince(Server server, List<String> prefixes, Date createdAfter) {
        if (prefixes == null || prefixes.isEmpty()) {
            return 0L;
        }

        Criteria[] prefixCriteria = prefixes.stream()
            .map(prefix -> Criteria.where(StorageFileDocumentFields.KEY).regex("^" + escapeRegex(prefix)))
            .toArray(Criteria[]::new);

        Criteria match = new Criteria().andOperator(
            new Criteria().orOperator(prefixCriteria),
            Criteria.where(StorageFileDocumentFields.CREATED_AT).gte(createdAfter)
        );

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(match),
            Aggregation.group().sum(StorageFileDocumentFields.SIZE).as("totalSize")
        );

        Document result = aggregate(server, aggregation, Document.class).getUniqueMappedResult();
        if (result == null) {
            return 0L;
        }
        return result.get("totalSize", Number.class).longValue();
    }

    public Map<String, Long> aggregateStorageByCategory(Server server) {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.group(StorageFileDocumentFields.CATEGORY).sum(StorageFileDocumentFields.SIZE).as("totalSize")
        );

        AggregationResults<Document> results = aggregate(server, aggregation, Document.class);

        Map<String, Long> byType = new HashMap<>();
        byType.put("ticket", 0L);
        byType.put("evidence", 0L);
        byType.put("logs", 0L);
        byType.put("backup", 0L);
        byType.put("replay", 0L);
        byType.put("other", 0L);

        for (Document doc : results.getMappedResults()) {
            String category = doc.getString("_id");
            long totalSize = doc.get("totalSize", Number.class).longValue();
            byType.put(category, totalSize);
        }

        return byType;
    }

    public long sumTotalSize(Server server) {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.group().sum(StorageFileDocumentFields.SIZE).as("totalSize")
        );

        AggregationResults<Document> results = aggregate(server, aggregation, Document.class);

        Document result = results.getUniqueMappedResult();
        if (result == null) {
            return 0L;
        }
        return result.get("totalSize", Number.class).longValue();
    }

    private String escapeRegex(String input) {
        return input.replaceAll("([\\\\^$.|?*+()\\[\\]{}])", "\\\\$1");
    }
}
