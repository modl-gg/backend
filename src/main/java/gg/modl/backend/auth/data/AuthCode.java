package gg.modl.backend.auth.data;

import gg.modl.backend.database.CollectionName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = CollectionName.AUTH_CODES)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthCode {
    @Id
    @Field
    @Indexed(name = "email_1", unique = true)
    private String email;

    @Field
    private String codeHash;

    @Field
    @Indexed(name = "expiresAt_1", expireAfter = "0s")
    private Instant expiresAt;
}
