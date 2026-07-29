package gg.modl.backend.replaylite.service;

import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.ratelimit.BucketPool;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReplayLiteAbuseGuard {
    private static final int MAX_INIT_ATTEMPTS_PER_HOUR = 40;
    private static final int MAX_CONFIRMS_PER_HOUR = 80;
    private static final int MAX_LABELS_PER_HOUR = 80;
    private static final int MAX_DOWNLOADS_PER_REPLAY_PER_HOUR = 120;
    private static final int MAX_REQUESTS_PER_IP_PER_MINUTE = 120;
    private static final int MAX_ACTIVE_PENDING_PER_SERVER = 25;

    private static final Duration HOURLY_WINDOW = Duration.ofHours(1);
    private static final Duration PER_MINUTE_WINDOW = Duration.ofMinutes(1);

    private static final String INIT_NAMESPACE = "replaylite:init";
    private static final String CONFIRM_NAMESPACE = "replaylite:confirm";
    private static final String LABEL_NAMESPACE = "replaylite:label";
    private static final String DOWNLOAD_NAMESPACE = "replaylite:download";
    private static final String IP_NAMESPACE = "replaylite:ip";

    private final BucketPool bucketPool;

    public void checkInit(UUID pluginServerUuid) {
        consume(INIT_NAMESPACE, pluginServerUuid.toString(), MAX_INIT_ATTEMPTS_PER_HOUR, HOURLY_WINDOW,
            "Too many Replay Lite upload attempts");
    }

    public void checkConfirm(UUID pluginServerUuid) {
        consume(CONFIRM_NAMESPACE, pluginServerUuid.toString(), MAX_CONFIRMS_PER_HOUR, HOURLY_WINDOW,
            "Too many Replay Lite confirm attempts");
    }

    public void checkLabel(String replayId) {
        consume(LABEL_NAMESPACE, replayId, MAX_LABELS_PER_HOUR, HOURLY_WINDOW,
            "Too many Replay Lite label submissions");
    }

    public void checkDownload(String replayId) {
        consume(DOWNLOAD_NAMESPACE, replayId, MAX_DOWNLOADS_PER_REPLAY_PER_HOUR, HOURLY_WINDOW,
            "Too many Replay Lite download requests");
    }

    public void checkIp(String clientIp) {
        consume(IP_NAMESPACE, clientIp, MAX_REQUESTS_PER_IP_PER_MINUTE, PER_MINUTE_WINDOW,
            "Too many Replay Lite requests");
    }

    public void checkPendingUploads(long activePendingCount) {
        if (activePendingCount >= MAX_ACTIVE_PENDING_PER_SERVER) {
            throw new ValidationException("Too many pending Replay Lite uploads");
        }
    }

    private void consume(String namespace, String key, int limit, Duration window, String message) {
        Bucket bucket = bucketPool.resolveBucket(namespace, key, limit, window);
        if (!bucket.tryConsume(1)) {
            throw new ValidationException(message);
        }
    }
}
