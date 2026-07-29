package gg.modl.backend.player.dto.response;

import java.util.List;
import java.util.Map;

public record PlayerReportsResult(List<Map<String, Object>> reports) {
}
