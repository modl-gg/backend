package gg.modl.backend.database.mongo.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.DeleteResult;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.repository.ReplayMongoRepository.ReplayCursor;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class ReplayMongoRepositoryTest {
    private static final Date CUTOFF = Date.from(Instant.parse("2026-05-18T12:00:00Z"));
    private static final Date CURSOR_CREATED_AT = Date.from(Instant.parse("2026-05-10T00:00:00Z"));
    private static final String CURSOR_ID = "cursor-id";

    @Test
    void findExpiredWithStorageKeyMatchesCompletedOrFailedWithStorageKeySortedAndCapped() {
        MongoTemplate template = mock(MongoTemplate.class);
        Server server = server();
        ReplayMongoRepository repository = repository(template, server);

        repository.findExpiredWithStorageKey(server, CUTOFF, null, 1000);

        Query query = captureFind(template);
        Map<String, Object> clauses = flattenAnd(query.getQueryObject());

        assertEquals(
            List.of(ReplayDocument.STATUS_COMPLETE, ReplayDocument.STATUS_FAILED),
            ((Document) clauses.get("status")).get("$in")
        );
        assertFalse(((List<?>) ((Document) clauses.get("status")).get("$in")).contains(ReplayDocument.STATUS_PENDING));
        assertEquals(CUTOFF, ((Document) clauses.get("createdAt")).get("$lt"));

        Document storageKey = (Document) clauses.get("storageKey");
        assertEquals(true, storageKey.get("$exists"));
        assertEquals(Arrays.asList(null, ""), storageKey.get("$nin"));

        Document sort = query.getSortObject();
        assertEquals(1, sort.get("createdAt"));
        assertEquals(1, sort.get("_id"));
        assertEquals(500, query.getLimit());
    }

    @Test
    void findExpiredWithStorageKeyAppliesKeysetPredicateWhenCursorProvided() {
        MongoTemplate template = mock(MongoTemplate.class);
        Server server = server();
        ReplayMongoRepository repository = repository(template, server);

        repository.findExpiredWithStorageKey(server, CUTOFF, new ReplayCursor(CURSOR_CREATED_AT, CURSOR_ID), 50);

        Query query = captureFind(template);
        Map<String, Object> clauses = flattenAnd(query.getQueryObject());

        assertEquals(CUTOFF, ((Document) clauses.get("createdAt")).get("$lt"));

        List<?> orBranches = (List<?>) clauses.get("$or");
        assertEquals(2, orBranches.size());
        Document greaterCreatedAt = (Document) ((Document) orBranches.get(0)).get("createdAt");
        assertEquals(CURSOR_CREATED_AT, greaterCreatedAt.get("$gt"));

        List<?> tieBreak = (List<?>) ((Document) orBranches.get(1)).get("$and");
        assertEquals(CURSOR_CREATED_AT, ((Document) tieBreak.get(0)).get("createdAt"));
        assertEquals(CURSOR_ID, ((Document) ((Document) tieBreak.get(1)).get("_id")).get("$gt"));

        assertEquals(50, query.getLimit());
    }

    @Test
    void findExpiredWithMissingStorageKeyMatchesAbsentNullOrBlankStorageKey() {
        MongoTemplate template = mock(MongoTemplate.class);
        Server server = server();
        ReplayMongoRepository repository = repository(template, server);

        repository.findExpiredWithMissingStorageKey(server, CUTOFF, null, 1000);

        Query query = captureFind(template);
        Map<String, Object> clauses = flattenAnd(query.getQueryObject());

        assertEquals(CUTOFF, ((Document) clauses.get("createdAt")).get("$lt"));

        List<?> orBranches = (List<?>) clauses.get("$or");
        assertEquals(3, orBranches.size());
        assertEquals(false, ((Document) ((Document) orBranches.get(0)).get("storageKey")).get("$exists"));
        assertTrue(((Document) orBranches.get(1)).containsKey("storageKey"));
        assertEquals(null, ((Document) orBranches.get(1)).get("storageKey"));
        assertEquals("", ((Document) orBranches.get(2)).get("storageKey"));

        Document sort = query.getSortObject();
        assertEquals(1, sort.get("createdAt"));
        assertEquals(1, sort.get("_id"));
        assertEquals(500, query.getLimit());
    }

    @Test
    void deleteByReplayIdsReturnsRemovedCount() {
        MongoTemplate template = mock(MongoTemplate.class);
        Server server = server();
        ReplayMongoRepository repository = repository(template, server);
        when(template.remove(any(Query.class), eq(ReplayDocument.class), eq(CollectionName.REPLAYS)))
            .thenReturn(DeleteResult.acknowledged(2L));

        assertEquals(2L, repository.deleteByReplayIds(server, List.of("a", "b")));
    }

    @Test
    void deleteByReplayIdsSkipsQueryWhenEmpty() {
        MongoTemplate template = mock(MongoTemplate.class);
        Server server = server();
        ReplayMongoRepository repository = repository(template, server);

        assertEquals(0L, repository.deleteByReplayIds(server, List.of()));
        assertEquals(0L, repository.deleteByReplayIds(server, null));

        verify(template, never()).remove(any(Query.class), eq(ReplayDocument.class), eq(CollectionName.REPLAYS));
    }

    private Query captureFind(MongoTemplate template) {
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(template).find(queryCaptor.capture(), eq(ReplayDocument.class), eq(CollectionName.REPLAYS));
        return queryCaptor.getValue();
    }

    private ReplayMongoRepository repository(MongoTemplate template, Server server) {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        when(tenantMongoAccess.forServer(server)).thenReturn(template);
        return new ReplayMongoRepository(tenantMongoAccess);
    }

    private Server server() {
        return new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
    }

    private static Map<String, Object> flattenAnd(Document query) {
        Map<String, Object> flattened = new LinkedHashMap<>();
        collectAnd(query, flattened);
        return flattened;
    }

    private static void collectAnd(Document node, Map<String, Object> flattened) {
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            if ("$and".equals(entry.getKey())) {
                for (Object child : (List<?>) entry.getValue()) {
                    collectAnd((Document) child, flattened);
                }
            } else {
                flattened.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
