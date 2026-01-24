package gg.modl.backend.staff.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitation {
    @Id
    private String id;

    @Indexed(name = "email_1")
    private String email;

    private String role;

    @Indexed(name = "token_1", unique = true)
    private String token;

    private Date expiresAt;

    @Builder.Default
    private String status = "pending";

    private Date createdAt;

    private Date updatedAt;
}
