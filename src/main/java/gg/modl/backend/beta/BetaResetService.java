package gg.modl.backend.beta;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.repository.AuthCodeMongoRepository;
import gg.modl.backend.database.mongo.repository.AuthSessionMongoRepository;
import gg.modl.backend.database.mongo.repository.ChatLogMongoRepository;
import gg.modl.backend.database.mongo.repository.CommandLogMongoRepository;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerLogMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.StorageFileMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketVerificationMongoRepository;
import gg.modl.backend.database.mongo.repository.WebAuthnChallengeMongoRepository;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BetaResetService {
    private final PlayerMongoRepository playerRepository;
    private final TicketMongoRepository ticketRepository;
    private final TicketVerificationMongoRepository ticketVerificationRepository;
    private final ChatLogMongoRepository chatLogRepository;
    private final CommandLogMongoRepository commandLogRepository;
    private final ServerLogMongoRepository serverLogRepository;
    private final ReplayMongoRepository replayRepository;
    private final StorageFileMongoRepository storageFileRepository;
    private final WebAuthnChallengeMongoRepository webAuthnChallengeRepository;
    private final AuthSessionMongoRepository authSessionRepository;
    private final AuthCodeMongoRepository authCodeRepository;
    private final ServerMongoRepository serverRepository;
    private final ServerService serverService;

    private final Set<String> resetsInFlight = ConcurrentHashMap.newKeySet();

    public List<String> reset(Server server) {
        List<String> cleared = withGuard(server, () -> clearTenant(server));
        serverService.evictAllServerCaches();
        return cleared;
    }

    @Async
    public CompletableFuture<List<ResetResult>> resetAll() {
        List<Server> servers = serverRepository.findAllBetaTesters();
        List<ResetResult> results = new ArrayList<>();
        boolean anyCleared = false;
        for (Server server : servers) {
            try {
                withGuard(server, () -> clearTenant(server));
                results.add(new ResetResult(server.getId(), server.getServerName(), true, "Reset successful"));
                anyCleared = true;
            } catch (Exception e) {
                log.warn("Beta reset-all failed for server {}", server.getId(), e);
                results.add(new ResetResult(server.getId(), server.getServerName(), false, e.getMessage()));
            }
        }
        if (anyCleared) {
            serverService.evictAllServerCaches();
        }
        return CompletableFuture.completedFuture(results);
    }

    private <T> T withGuard(Server server, Supplier<T> action) {
        if (!resetsInFlight.add(server.getId())) {
            throw new BetaRequestException("A reset is already in progress for this server.", HttpStatus.CONFLICT);
        }
        try {
            return action.get();
        } finally {
            resetsInFlight.remove(server.getId());
        }
    }

    private List<String> clearTenant(Server server) {
        List<String> cleared = new ArrayList<>();
        for (Map.Entry<String, Runnable> step : clearSteps(server).entrySet()) {
            try {
                step.getValue().run();
                cleared.add(step.getKey());
            } catch (Exception e) {
                log.error("Beta reset failed clearing {} for server {}", step.getKey(), server.getId(), e);
            }
        }
        serverRepository.resetUsageAndStatsCounters(server.getId());
        return cleared;
    }

    private Map<String, Runnable> clearSteps(Server server) {
        Map<String, Runnable> steps = new LinkedHashMap<>();
        steps.put(CollectionName.PLAYERS, () -> playerRepository.remove(server, new Query()));
        steps.put(CollectionName.TICKETS, () -> ticketRepository.remove(server, new Query()));
        steps.put(CollectionName.TICKET_VERIFICATIONS, () -> ticketVerificationRepository.remove(server, new Query()));
        steps.put(CollectionName.CHAT_LOGS, () -> chatLogRepository.remove(server, new Query()));
        steps.put(CollectionName.COMMAND_LOGS, () -> commandLogRepository.remove(server, new Query()));
        steps.put(CollectionName.LOGS, () -> serverLogRepository.remove(server, new Query()));
        steps.put(CollectionName.REPLAYS, () -> replayRepository.remove(server, new Query()));
        steps.put(CollectionName.STORAGE_FILES, () -> storageFileRepository.remove(server, new Query()));
        steps.put(CollectionName.WEBAUTHN_CHALLENGES, () -> webAuthnChallengeRepository.remove(server, new Query()));
        steps.put(CollectionName.SESSIONS, () -> authSessionRepository.deleteAllForServer(server));
        steps.put(CollectionName.AUTH_CODES, () -> authCodeRepository.deleteAllForServer(server));
        return steps;
    }
}
