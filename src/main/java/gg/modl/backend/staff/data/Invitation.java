package gg.modl.backend.staff.data;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = CollectionName.INVITATIONS)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@GenerateMongoFields
public class Invitation {
    @Id
    private String id;

    @Indexed(name = "idx_invitations_email")
    private String email;

    private String role;

    @Indexed(name = "uidx_invitations_token", unique = true)
    private String token;

    @Indexed(name = "idx_invitations_expiresAt_ttl", expireAfter = "0s")
    private Date expiresAt;

    private Date createdAt;

    private Date updatedAt;
}
