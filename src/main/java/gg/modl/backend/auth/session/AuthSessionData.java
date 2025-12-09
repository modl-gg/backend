package gg.modl.backend.auth.session;

import gg.modl.backend.database.CollectionName;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = CollectionName.SESSIONS)
@Data
public class AuthSessionData {
    @Id
    private String id;

    @Field
    @Indexed
    private String email;

    @Field
    private Instant createdAt;

    @Field
    @Indexed(expireAfter = "0s")
    private Instant expiresAt;
}
