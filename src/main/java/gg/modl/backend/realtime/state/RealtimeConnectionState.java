package gg.modl.backend.realtime.state;

import gg.modl.backend.realtime.auth.RealtimeClientKind;
import gg.modl.backend.realtime.auth.RealtimePrincipal;
import gg.modl.proto.modl.v1.Topic;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.Nullable;

public class RealtimeConnectionState {
    private final String connectionId = UUID.randomUUID().toString();
    private final Instant connectedAt = Instant.now();
    private final Set<Topic> subscriptions = EnumSet.noneOf(Topic.class);
    private final Object sendLock = new Object();
    private final AtomicLong deliveryAttempts = new AtomicLong();
    private final AtomicLong deliveryFailures = new AtomicLong();
    private volatile Instant lastHeartbeat = Instant.now();
    private volatile RealtimePrincipal principal;
    private volatile int protocolVersion;
    private volatile String lastAcknowledgedEventId;
    private volatile boolean closing;
    private volatile Instant terminalSince;

    public String getConnectionId() {
        return connectionId;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public boolean isAuthenticated() {
        return principal != null;
    }

    public void authenticate(RealtimePrincipal principal, int protocolVersion) {
        this.principal = principal;
        this.protocolVersion = protocolVersion;
    }

    public boolean isClosing() {
        return closing;
    }

    public void markClosing() {
        closing = true;
    }

    public void markTerminal() {
        if (terminalSince == null) {
            terminalSince = Instant.now();
        }
    }

    @Nullable
    public Instant getTerminalSince() {
        return terminalSince;
    }

    public RealtimePrincipal getPrincipal() {
        return principal;
    }

    public RealtimeClientKind getClientKind() {
        return principal != null ? principal.clientKind() : null;
    }

    @Nullable
    public String getTenantId() {
        return principal != null ? principal.serverId() : null;
    }

    @Nullable
    public String getServerId() {
        return principal != null ? principal.serverId() : null;
    }

    public Set<Topic> getSubscriptions() {
        synchronized (subscriptions) {
            return Set.copyOf(subscriptions);
        }
    }

    public void subscribe(Topic topic) {
        synchronized (subscriptions) {
            subscriptions.add(topic);
        }
    }

    public void unsubscribe(Topic topic) {
        synchronized (subscriptions) {
            subscriptions.remove(topic);
        }
    }

    public boolean isSubscribedTo(Topic topic) {
        synchronized (subscriptions) {
            return subscriptions.contains(topic);
        }
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void recordHeartbeat() {
        lastHeartbeat = Instant.now();
    }

    public void setLastAcknowledgedEventId(@Nullable String lastAcknowledgedEventId) {
        this.lastAcknowledgedEventId = lastAcknowledgedEventId;
    }

    public Object getSendLock() {
        return sendLock;
    }

    public long recordDeliveryAttempt() {
        return deliveryAttempts.incrementAndGet();
    }

    public long recordDeliveryFailure() {
        return deliveryFailures.incrementAndGet();
    }
}
