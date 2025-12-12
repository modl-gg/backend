package gg.modl.backend.auth.data;

import gg.modl.backend.database.CollectionName;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = CollectionName.AUTH_CODES)
@Data
public class AuthCode {
    @Id
    @Field
    @Indexed(unique = true)
    private String email;

    @Field
    private String codeHash;

    @Field
    @Indexed(expireAfter = "0s")
    private Instant expiresAt;
}
