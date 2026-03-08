package gg.modl.backend.auth.data;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Document(collection = CollectionName.WEBAUTHN_CHALLENGES)
@GenerateMongoFields
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnChallenge {
    @Id
    private String id;

    @Field
    private String challengeJson;

    @Field
    private String email;

    @Field
    private Date expiresAt;
}
