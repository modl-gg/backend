package gg.modl.backend.ticket.service;

import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.response.SubscriptionUpdateResponse;
import gg.modl.backend.ticket.dto.response.TicketSubscriptionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketSubscriptionService {
    private final DynamicMongoTemplateProvider mongoProvider;

    public List<TicketSubscriptionResponse> getSubscriptions(Server server, String staffEmail) {
        MongoTemplate template = getTemplate(server);

        Query staffQuery = Query.query(Criteria.where("email").is(staffEmail));
        Staff staff = template.findOne(staffQuery, Staff.class, CollectionName.STAFF);

        if (staff == null || staff.getSubscribedTickets() == null || staff.getSubscribedTickets().isEmpty()) {
            return Collections.emptyList();
        }

        List<TicketSubscriptionResponse> subscriptions = new ArrayList<>();

        for (Staff.TicketSubscription subscription : staff.getSubscribedTickets()) {
            if (!subscription.isActive()) {
                continue;
            }

            Query ticketQuery = Query.query(Criteria.where("_id").is(subscription.getTicketId()));
            Ticket ticket = template.findOne(ticketQuery, Ticket.class, CollectionName.TICKETS);

            if (ticket != null) {
                String title = ticket.getId() + ": " + (ticket.getSubject() != null ? ticket.getSubject() : "Untitled Ticket");
                subscriptions.add(new TicketSubscriptionResponse(
                        subscription.getTicketId(),
                        title,
                        subscription.getSubscribedAt()
                ));
            }
        }

        return subscriptions;
    }

    public boolean unsubscribe(Server server, String staffEmail, String ticketId) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(
                Criteria.where("email").is(staffEmail)
                        .and("subscribedTickets.ticketId").is(ticketId)
                        .and("subscribedTickets.active").is(true)
        );

        Update update = new Update().set("subscribedTickets.$.active", false);

        UpdateResult result = template.updateFirst(query, update, Staff.class, CollectionName.STAFF);
        return result.getModifiedCount() > 0;
    }

    public List<SubscriptionUpdateResponse> getUpdates(Server server, String staffEmail, int limit) {
        MongoTemplate template = getTemplate(server);

        Query staffQuery = Query.query(Criteria.where("email").is(staffEmail));
        Staff staff = template.findOne(staffQuery, Staff.class, CollectionName.STAFF);

        if (staff == null || staff.getSubscribedTickets() == null || staff.getSubscribedTickets().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> subscribedTicketIds = staff.getSubscribedTickets().stream()
                .filter(Staff.TicketSubscription::isActive)
                .map(Staff.TicketSubscription::getTicketId)
                .toList();

        if (subscribedTicketIds.isEmpty()) {
            return Collections.emptyList();
        }

        Query ticketQuery = Query.query(
                Criteria.where("_id").in(subscribedTicketIds)
                        .and("replies.0").exists(true)
        );

        List<Ticket> tickets = template.find(ticketQuery, Ticket.class, CollectionName.TICKETS);

        List<SubscriptionUpdateResponse> updates = new ArrayList<>();

        for (Ticket ticket : tickets) {
            Staff.TicketSubscription subscription = staff.getSubscribedTickets().stream()
                    .filter(sub -> sub.getTicketId().equals(ticket.getId()))
                    .findFirst()
                    .orElse(null);

            if (subscription == null || ticket.getReplies() == null) {
                continue;
            }

            List<TicketReply> recentReplies = ticket.getReplies().stream()
                    .filter(reply -> reply.getCreated() != null && reply.getCreated().after(subscription.getSubscribedAt()))
                    .sorted((a, b) -> b.getCreated().compareTo(a.getCreated()))
                    .toList();

            List<TicketReply> unreadReplies = recentReplies.stream()
                    .filter(reply -> subscription.getLastReadAt() == null || reply.getCreated().after(subscription.getLastReadAt()))
                    .toList();

            if (!unreadReplies.isEmpty()) {
                TicketReply latestReply = unreadReplies.get(0);
                String ticketTitle = ticket.getId() + ": " + (ticket.getSubject() != null ? ticket.getSubject() : "Untitled Ticket");

                updates.add(new SubscriptionUpdateResponse(
                        ticket.getId() + "-" + latestReply.getId(),
                        ticket.getId(),
                        ticketTitle,
                        latestReply.getContent(),
                        latestReply.getName(),
                        latestReply.getCreated(),
                        latestReply.isStaff(),
                        false,
                        unreadReplies.size() > 1 ? unreadReplies.size() - 1 : null
                ));
            }

            if (updates.size() >= limit) {
                break;
            }
        }

        updates.sort((a, b) -> b.replyAt().compareTo(a.replyAt()));
        return updates.stream().limit(limit).toList();
    }

    public boolean markAsRead(Server server, String staffEmail, String updateId) {
        String ticketId = updateId.split("-")[0];

        MongoTemplate template = getTemplate(server);

        Query query = Query.query(
                Criteria.where("email").is(staffEmail)
                        .and("subscribedTickets.ticketId").is(ticketId)
                        .and("subscribedTickets.active").is(true)
        );

        Update update = new Update().set("subscribedTickets.$.lastReadAt", new Date());

        UpdateResult result = template.updateFirst(query, update, Staff.class, CollectionName.STAFF);
        return result.getModifiedCount() > 0;
    }

    public void ensureSubscription(Server server, String ticketId, String staffEmail) {
        MongoTemplate template = getTemplate(server);

        Query staffQuery = Query.query(Criteria.where("email").is(staffEmail));
        Staff staff = template.findOne(staffQuery, Staff.class, CollectionName.STAFF);

        if (staff == null) {
            log.warn("Staff member {} not found", staffEmail);
            return;
        }

        if (staff.getSubscribedTickets() != null) {
            boolean alreadySubscribed = staff.getSubscribedTickets().stream()
                    .anyMatch(sub -> sub.getTicketId().equals(ticketId) && sub.isActive());

            if (alreadySubscribed) {
                return;
            }
        }

        Staff.TicketSubscription subscription = new Staff.TicketSubscription();
        subscription.setTicketId(ticketId);
        subscription.setSubscribedAt(new Date());
        subscription.setActive(true);

        Update update = new Update().addToSet("subscribedTickets", subscription);
        template.updateFirst(staffQuery, update, Staff.class, CollectionName.STAFF);
    }

    public void markTicketAsRead(Server server, String ticketId, String staffEmail) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(
                Criteria.where("email").is(staffEmail)
                        .and("subscribedTickets.ticketId").is(ticketId)
                        .and("subscribedTickets.active").is(true)
        );

        Update update = new Update().set("subscribedTickets.$.lastReadAt", new Date());
        template.updateFirst(query, update, Staff.class, CollectionName.STAFF);
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }
}
