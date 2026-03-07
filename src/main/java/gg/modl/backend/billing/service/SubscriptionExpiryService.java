package gg.modl.backend.billing.service;

import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryService {
    private final ServerMongoRepository serverRepository;
    private final UsageTrackingService usageTrackingService;

    @Scheduled(fixedRate = 3600000)
    public void checkExpiredSubscriptions() {
        try {
            Query query = Query.query(new Criteria().andOperator(
                    MongoQueries.where(ServerFields.SUBSCRIPTION_STATUS).is(SubscriptionStatus.CANCELED),
                    Criteria.where(ServerFields.CURRENT_PERIOD_END.path()).exists(true).ne(null)
            ));

            List<Server> cancelledServers = serverRepository.find(query);
            Date now = new Date();

            for (Server server : cancelledServers) {
                Date endDate = server.getCurrentPeriodEnd();
                if (endDate != null && endDate.before(now)) {
                    mutateServer(server, current -> {
                        current.setSubscriptionStatus(SubscriptionStatus.INACTIVE);
                        current.setPlan(ServerPlan.FREE);
                        current.setCurrentPeriodEnd(null);
                    });
                    usageTrackingService.resetUsageCounters(server.getId());
                }
            }
        } catch (Exception exception) {
            log.error("Error checking for expired subscriptions", exception);
        }
    }

    private void mutateServer(Server server, Consumer<Server> mutator) {
        Server original = serverRepository.snapshot(server);
        mutator.accept(server);
        serverRepository.saveChanges(original, server);
    }
}