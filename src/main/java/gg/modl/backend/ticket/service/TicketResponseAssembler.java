package gg.modl.backend.ticket.service;

import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketStatus;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketResponseAssembler {
    private final StaffMongoRepository staffRepository;

    private static final String AVATAR_URL_FORMAT = "https://mc-heads.net/avatar/%s/32";

    public TicketResponse toTicketResponse(Server server, Ticket ticket) {
        List<TicketReply> processedReplies = processRepliesWithNames(server, ticket);
        String creatorName = ticket.getCreatorName() != null ? ticket.getCreatorName() : "Unknown";

        return new TicketResponse(
            ticket.getId(),
            ticket.getType() != null ? ticket.getType().getId() : TicketCategory.SUPPORT.getId(),
            ticket.getType() != null ? ticket.getType().getDisplayName() : TicketCategory.SUPPORT.getDisplayName(),
            ticket.getSubject() != null ? ticket.getSubject() : "No Subject",
            ticket.getStatus() != null ? ticket.getStatus().getId() : TicketStatus.OPEN.getId(),
            ticket.getAppealWorkflowStatus() != null ? ticket.getAppealWorkflowStatus().getId() : null,
            creatorName,
            ticket.getCreatorUuid(),
            creatorName,
            ticket.getReportedPlayer(),
            ticket.getReportedPlayerUuid(),
            ticket.getCreated(),
            ticket.isLocked(),
            processedReplies,
            ticket.getNotes(),
            ticket.getTags(),
            ticket.getFormData(),
            ticket.getData(),
            ticket.getChatMessages(),
            ticket.getAiAnalysis(),
            ticket.isEmailAuthEnabled(),
            ticket.isHidden(),
            ticket.getReplayUrl(),
            ticket.getAssignedTo()
        );
    }

    private List<TicketReply> processRepliesWithNames(Server server, Ticket ticket) {
        if (ticket.getReplies() == null || ticket.getReplies().isEmpty()) {
            return ticket.getReplies();
        }

        String creatorName = ticket.getCreatorName() != null ? ticket.getCreatorName() : "Player";

        Set<String> staffUsernames = new HashSet<>();
        for (TicketReply reply : ticket.getReplies()) {
            if (reply.isStaff() && (reply.getAvatar() == null || reply.getAvatar().isBlank()) && reply.getName() != null && !reply.getName().isBlank()) {
                staffUsernames.add(reply.getName());
            }
        }

        Map<String, String> staffAvatarMap = new HashMap<>();
        if (!staffUsernames.isEmpty()) {
            Map<String, Staff> staffByUsername = staffRepository.findByUsernames(server, staffUsernames)
                .stream()
                .collect(Collectors.toMap(Staff::getUsername, Function.identity(), (a, b) -> a));

            for (String username : staffUsernames) {
                Staff staff = staffByUsername.get(username);
                if (staff != null && staff.getAssignedMinecraftUuid() != null && !staff.getAssignedMinecraftUuid().isBlank()) {
                    staffAvatarMap.put(username, String.format(AVATAR_URL_FORMAT, staff.getAssignedMinecraftUuid()));
                }
            }
        }

        return ticket.getReplies()
            .stream().map(reply -> {
                String name = reply.getName();
                if (name == null || name.isBlank()) {
                    name = reply.isStaff() ? "Staff" : creatorName;
                }
                String type = reply.getType();
                if (type == null || type.isBlank()) {
                    type = reply.isStaff() ? "staff" : "user";
                }
                String avatar = reply.getAvatar();
                if (reply.isStaff() && (avatar == null || avatar.isBlank()) && name != null) {
                    String staffAvatar = staffAvatarMap.get(name);
                    if (staffAvatar != null) {
                        avatar = staffAvatar;
                    }
                }
                return reply.toBuilder().name(name).type(type).avatar(avatar).build();
            }).toList();
    }
}
