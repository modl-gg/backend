package gg.modl.backend.player.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NoteEntry {
    @Field("id")
    private String id;

    private String text;
    private Date date;
    private String issuerName;
    private String issuerId;
}
