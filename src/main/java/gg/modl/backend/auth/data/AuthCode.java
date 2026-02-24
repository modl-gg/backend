package gg.modl.backend.auth.data;

import gg.modl.backend.database.CollectionName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Document(collection = CollectionName.AUTH_CODES)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthCode {
    @Id
    @Field
    @Indexed(name = "uidx_auth_codes_email", unique = true)
    private String email;

    @Field
    private String codeHash;

    @Field
    private int failedAttempts;

    @Field
    @Indexed(name = "idx_auth_codes_expiresAt_ttl", expireAfter = "0s")
    private Date expiresAt;
}
