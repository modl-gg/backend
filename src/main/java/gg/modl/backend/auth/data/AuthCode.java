package gg.modl.backend.auth.data;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = CollectionName.AUTH_CODES)
@GenerateMongoFields
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthCode {
    @Id
    private String email;

    @Field("codeHash")
    private String codeHash;

    @Field("failedAttempts")
    private int failedAttempts;

    @Field("expiresAt")
    private Date expiresAt;
}
