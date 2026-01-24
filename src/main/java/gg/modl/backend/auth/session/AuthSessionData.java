package gg.modl.backend.auth.session;

import gg.modl.backend.database.CollectionName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = CollectionName.SESSIONS)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthSessionData {
    @Id
    private String id;

    @Field
    @Indexed(name = "email_1")
    private String email;

    @Field
    private Instant createdAt;

    @Field
    @Indexed(name = "expiresAt_1", expireAfter = "0s")
    private Instant expiresAt;
}
