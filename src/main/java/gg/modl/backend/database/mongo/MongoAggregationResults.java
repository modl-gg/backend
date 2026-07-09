package gg.modl.backend.database.mongo;

import java.util.List;
import org.bson.Document;
import org.bson.types.Decimal128;

public final class MongoAggregationResults {
    private static final String FACET_COUNT_FIELD = "n";

    private MongoAggregationResults() {
    }

    public static long extractLong(Document document, String fieldName) {
        if (document == null) {
            return 0L;
        }
        return coerceLong(document.get(fieldName));
    }

    public static long extractFacetCount(Document facets, String facetKey) {
        if (facets == null) {
            return 0L;
        }
        List<Document> entries = facets.getList(facetKey, Document.class, List.of());
        if (entries.isEmpty()) {
            return 0L;
        }
        return coerceLong(entries.getFirst().get(FACET_COUNT_FIELD));
    }

    private static long coerceLong(Object value) {
        if (value instanceof Decimal128 decimal) {
            return decimal.longValue();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
