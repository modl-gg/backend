package gg.modl.backend.homepage.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class HomepageCard {
    @Id
    private String id;

    private String title;
    private String description;
    private String icon;

    private String iconColor;

    private String actionType;

    private String actionUrl;

    private String actionButtonText;

    private String categoryId;

    private String backgroundColor;

    private int ordinal;

    private boolean isEnabled;

    private Date createdAt;

    private Date updatedAt;
}
