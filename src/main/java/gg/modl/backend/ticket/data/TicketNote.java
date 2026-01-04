package gg.modl.backend.ticket.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketNote {
    private String text;
    private String issuerName;
    private String issuerAvatar;
    private Date date;
}
