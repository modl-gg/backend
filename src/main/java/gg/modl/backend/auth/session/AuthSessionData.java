package gg.modl.backend.auth.session;

import gg.modl.backend.database.CollectionName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Document(collection = CollectionName.SESSIONS)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthSessionData {
    @Id
    private String id;

    @Field
    @Indexed(name = "idx_sessions_email")
    private String email;

    @Field
    private Date createdAt;

    @Field
    @Indexed(name = "idx_sessions_expiresAt_ttl", expireAfter = "0s")
    private Date expiresAt;

    @Field
    private String ipAddress;

    @Field
    private String userAgent;
}
