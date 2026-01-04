package gg.modl.backend.role.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Document
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffRole {
    @Id
    private String mongoId;

    @Indexed(unique = true)
    private String id;

    private String name;

    private String description;

    @Builder.Default
    private List<String> permissions = new ArrayList<>();

    @Builder.Default
    private boolean isDefault = false;

    @Builder.Default
    private int order = 999;

    private Date createdAt;

    private Date updatedAt;
}
