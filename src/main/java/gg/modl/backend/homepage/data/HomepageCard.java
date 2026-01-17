package gg.modl.backend.homepage.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

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
    
    @Field("icon_color")
    @JsonProperty("icon_color")
    private String iconColor;
    
    @Field("action_type")
    @JsonProperty("action_type")
    private String actionType;
    
    @Field("action_url")
    @JsonProperty("action_url")
    private String actionUrl;
    
    @Field("action_button_text")
    @JsonProperty("action_button_text")
    private String actionButtonText;
    
    @Field("category_id")
    @JsonProperty("category_id")
    private String categoryId;
    
    @Field("background_color")
    @JsonProperty("background_color")
    private String backgroundColor;
    
    private int ordinal;
    
    @Field("is_enabled")
    @JsonProperty("is_enabled")
    private boolean isEnabled;

    @Field("created_at")
    @JsonProperty("created_at")
    private Date createdAt;
    
    @Field("updated_at")
    @JsonProperty("updated_at")
    private Date updatedAt;
}
