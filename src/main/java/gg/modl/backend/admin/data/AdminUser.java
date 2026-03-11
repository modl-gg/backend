package gg.modl.backend.admin.data;

import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "admin_users")
@GenerateMongoFields
public class AdminUser {
    @Id
    private String id;
    private String email;
    private List<String> loggedInIps = new ArrayList<>();
    private Date lastActivityAt;
    private Date createdAt;

    public AdminUser(String email) {
        this();
        this.email = email.toLowerCase().trim();
    }

    public AdminUser() {
        this.createdAt = new Date();
        this.lastActivityAt = new Date();
    }
}
