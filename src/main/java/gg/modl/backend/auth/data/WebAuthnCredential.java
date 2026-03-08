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

@Document(collection = CollectionName.WEBAUTHN_CREDENTIALS)
@GenerateMongoFields
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnCredential {
    @Id
    private String id;

    @Field
    private String email;

    @Field
    private String credentialId;

    @Field
    private byte[] publicKeyCose;

    @Field
    private long signatureCount;

    @Field
    private String userHandle;

    @Field
    private String name;

    @Field
    private Date createdAt;

    @Field
    private Date lastUsedAt;
}
