package gg.modl.backend.knowledgebase.dto.request;

import java.util.List;

public record ReorderRequest(
        List<String> ids
) {
}
