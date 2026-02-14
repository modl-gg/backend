package gg.modl.backend.ticket.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document
public class TicketVerification {
    @Id
    private String id;

    private String ticketId;
    private String token;
    private String codeHash;
    private String email;
    private Date expiresAt;
}
