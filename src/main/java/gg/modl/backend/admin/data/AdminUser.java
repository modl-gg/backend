package gg.modl.backend.admin.data;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@Document(collection = "admin_users")
public class AdminUser {
    @Id
    private String id;
    private String email;
    private List<String> loggedInIps = new ArrayList<>();
    private Date lastActivityAt;
    private Date createdAt;

    public AdminUser() {
        this.createdAt = new Date();
        this.lastActivityAt = new Date();
    }

    public AdminUser(String email) {
        this();
        this.email = email.toLowerCase().trim();
    }
}
