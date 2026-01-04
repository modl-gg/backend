package gg.modl.backend.billing.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final UsageTrackingService usageTrackingService;

    @Scheduled(fixedRate = 3600000)
    public void checkExpiredSubscriptions() {
        try {
            MongoTemplate globalDb = mongoProvider.getGlobalDatabase();

            Query query = Query.query(
                    Criteria.where("subscription_status").is(SubscriptionStatus.canceled)
                            .and("current_period_end").exists(true).ne(null)
            );

            List<Server> cancelledServers = globalDb.find(query, Server.class, CollectionName.MODL_SERVERS);

            Date now = new Date();
            int expiredCount = 0;

            for (Server server : cancelledServers) {
                Date endDate = server.getCurrentPeriodEnd();
                if (endDate != null && endDate.before(now)) {
                    Update update = new Update()
                            .set("subscription_status", SubscriptionStatus.inactive)
                            .set("plan", ServerPlan.free)
                            .unset("current_period_end");

                    Query updateQuery = Query.query(Criteria.where("_id").is(server.getId()));
                    globalDb.updateFirst(updateQuery, update, CollectionName.MODL_SERVERS);

                    usageTrackingService.resetUsageCounters(server.getId());

                    expiredCount++;
                    log.info("Server {} subscription expired and downgraded to free", server.getServerName());
                }
            }

            if (expiredCount > 0) {
                log.info("Expired and downgraded {} cancelled subscriptions", expiredCount);
            }
        } catch (Exception e) {
            log.error("Error checking for expired subscriptions", e);
        }
    }
}
