package gg.modl.backend.settings.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Label {
    private String id;          // UUID
    private String name;        // e.g., "bug", "critical"
    private String color;       // Hex color e.g., "#d73a4a"
    private String description; // Optional description
}
