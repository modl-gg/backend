package gg.modl.backend.ticket.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketReply {
    private String id;
    private String name;
    private String avatar;
    private String content;
    private String type;
    private Date created;
    private boolean staff;
    private String action;
    private List<String> attachments;
    private String creatorIdentifier;
}
