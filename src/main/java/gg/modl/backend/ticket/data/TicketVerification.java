package gg.modl.backend.ticket.data;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = CollectionName.TICKET_VERIFICATIONS)
@GenerateMongoFields
public class TicketVerification {
    @Id
    private String id;

    private String ticketId;
    private String token;
    private String codeHash;
    private String email;
    @Indexed(name = "idx_ticket_verifications_expiresAt_ttl", expireAfter = "0s")
    private Date expiresAt;
}
