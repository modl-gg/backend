package gg.modl.backend.replaylite.service;

import gg.modl.backend.infrastructure.exception.ValidationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReplayLiteAbuseGuard {
    private static final int MAX_TRACKED_KEYS = 50_000;
    private static final int MAX_INIT_ATTEMPTS_PER_HOUR = 40;
    private static final int MAX_CONFIRMS_PER_HOUR = 80;
    private static final int MAX_LABELS_PER_HOUR = 80;
    private static final int MAX_DOWNLOADS_PER_REPLAY_PER_HOUR = 120;
    private static final int MAX_REQUESTS_PER_IP_PER_MINUTE = 120;
    private static final int MAX_ACTIVE_PENDING_PER_SERVER = 25;

    private final Clock clock;
    private final Map<String, WindowCounter> counters = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, WindowCounter> eldest) {
            return size() > MAX_TRACKED_KEYS;
        }
    };

    public ReplayLiteAbuseGuard() {
        this(Clock.systemUTC());
    }

    ReplayLiteAbuseGuard(Clock clock) {
        this.clock = clock;
    }

    public void checkInit(UUID pluginServerUuid) {
        consume("init:" + pluginServerUuid, MAX_INIT_ATTEMPTS_PER_HOUR, Duration.ofHours(1), "Too many Replay Lite upload attempts");
    }

    public void checkConfirm(UUID pluginServerUuid) {
        consume("confirm:" + pluginServerUuid, MAX_CONFIRMS_PER_HOUR, Duration.ofHours(1), "Too many Replay Lite confirm attempts");
    }

    public void checkLabel(String replayId) {
        consume("label:" + replayId, MAX_LABELS_PER_HOUR, Duration.ofHours(1), "Too many Replay Lite label submissions");
    }

    public void checkDownload(String replayId) {
        consume("download:" + replayId, MAX_DOWNLOADS_PER_REPLAY_PER_HOUR, Duration.ofHours(1), "Too many Replay Lite download requests");
    }

    public void checkIp(String clientIp) {
        consume("ip:" + clientIp, MAX_REQUESTS_PER_IP_PER_MINUTE, Duration.ofMinutes(1), "Too many Replay Lite requests");
    }

    public void checkPendingUploads(long activePendingCount) {
        if (activePendingCount >= MAX_ACTIVE_PENDING_PER_SERVER) {
            throw new ValidationException("Too many pending Replay Lite uploads");
        }
    }

    private synchronized void consume(String key, int limit, Duration window, String message) {
        Instant now = clock.instant();
        WindowCounter counter = counters.get(key);
        if (counter == null || !now.isBefore(counter.expiresAt())) {
            counters.put(key, new WindowCounter(1, now.plus(window)));
            return;
        }

        if (counter.count() >= limit) {
            throw new ValidationException(message);
        }

        counters.put(key, new WindowCounter(counter.count() + 1, counter.expiresAt()));
    }

    private record WindowCounter(int count, Instant expiresAt) {}
}
