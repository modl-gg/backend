package gg.modl.backend.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;

class MongoIndexModelGoldenTest {
    private static final String GOLDEN_RESOURCE = "golden/mongo-index-model.txt";

    @Test
    void desiredIndexModelMatchesGolden() {
        String actual = renderDesiredIndexModel();
        writeActualForInspection(actual);
        String expected = readGolden();
        assertThat(actual).isEqualTo(expected);
    }

    private String renderDesiredIndexModel() {
        Map<String, List<IndexDefinition>> captured = new ConcurrentHashMap<>();

        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate globalTemplate = recordingTemplate(captured);
        MongoTemplate trainingTemplate = recordingTemplate(captured);
        MongoTemplate tenantTemplate = recordingTemplate(captured);

        when(tenantMongoAccess.global()).thenReturn(globalTemplate);
        when(tenantMongoAccess.forDatabase(CollectionName.TRAINING_DATABASE)).thenReturn(trainingTemplate);

        MongoIndexReconciler reconciler = new MongoIndexReconciler(tenantMongoAccess);
        reconciler.initGlobalIndexes();
        reconciler.createTenantIndexes(tenantTemplate);

        return render(captured);
    }

    private MongoTemplate recordingTemplate(Map<String, List<IndexDefinition>> captured) {
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.indexOps(anyString())).thenAnswer(invocation -> {
            String collection = invocation.getArgument(0);
            IndexOperations ops = mock(IndexOperations.class);
            when(ops.getIndexInfo()).thenReturn(List.of());
            when(ops.createIndex(any())).thenAnswer(created -> {
                captured.computeIfAbsent(collection, key -> new ArrayList<>())
                    .add(created.getArgument(0));
                return collection;
            });
            return ops;
        });
        return template;
    }

    private String render(Map<String, List<IndexDefinition>> captured) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<IndexDefinition>> entry : new TreeMap<>(captured).entrySet()) {
            List<String> lines = new ArrayList<>();
            for (IndexDefinition definition : entry.getValue()) {
                lines.add(canonicalIndex(definition));
            }
            lines.sort(String::compareTo);
            for (String line : lines) {
                builder.append(entry.getKey()).append('\t').append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private String canonicalIndex(IndexDefinition definition) {
        return "keys=" + canonical(definition.getIndexKeys())
            + " options=" + canonical(definition.getIndexOptions());
    }

    private String canonical(Object value) {
        if (value instanceof Document document) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : new TreeMap<>(document).entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(entry.getKey()).append('=').append(canonical(entry.getValue()));
                first = false;
            }
            return builder.append('}').toString();
        }
        if (value instanceof List<?> elements) {
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < elements.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(canonical(elements.get(i)));
            }
            return builder.append(']').toString();
        }
        return String.valueOf(value);
    }

    private void writeActualForInspection(String actual) {
        try {
            Path target = Path.of("build", "mongo-index-model.actual.txt");
            Files.createDirectories(target.getParent());
            Files.writeString(target, actual, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String readGolden() {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(GOLDEN_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing golden resource " + GOLDEN_RESOURCE
                    + "; inspect build/mongo-index-model.actual.txt to seed it");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
