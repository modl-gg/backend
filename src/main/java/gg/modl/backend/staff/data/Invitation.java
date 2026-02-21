package gg.modl.backend.staff.data;

import gg.modl.backend.database.CollectionName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = CollectionName.INVITATIONS)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
