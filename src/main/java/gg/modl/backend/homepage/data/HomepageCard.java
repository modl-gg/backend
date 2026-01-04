package gg.modl.backend.homepage.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomepageCard {
    @Id
    private String id;

    private String title;
    private String description;
    private String icon;
    private String actionType;
    private String actionValue;
    private int ordinal;
    private boolean isVisible;

    private Date createdAt;
    private Date updatedAt;
}
