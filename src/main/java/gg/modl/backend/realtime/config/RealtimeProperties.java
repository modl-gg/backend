package gg.modl.backend.realtime.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "modl.realtime.ws")
public class RealtimeProperties {
    private boolean enabled = false;

    private String publicUrl;

    @Min(1)
    private int protocolVersion = 1;

    @Min(1024)
    @Max(1024 * 1024)
    private int maxFrameBytes = 65_536;

    @Min(5)
    private long heartbeatTimeoutSeconds = 60;

    /**
     * Interval between unsolicited server -> client heartbeats. Must stay comfortably below the
     * client-side inbound liveness timeout (the Minecraft plugin force-reconnects after 75s of
     * silence), so the default gives roughly three heartbeats per client window.
     */
    @Min(1000)
    private long serverHeartbeatIntervalMs = 25_000;

    @Min(1)
    private long handshakeTimeoutSeconds = 10;

    @Min(1)
    private int inboundRateLimitMessages = 120;

    @Min(1)
    private long inboundRateLimitWindowSeconds = 10;

    @Min(1000)
    private long heartbeatSweepIntervalMs = 15000;

    @Min(1000)
    @Max(4999)
    private int deployDrainCloseCode = 1012;

    @Min(0)
    private int deployDrainRetryAfterMs = 5000;

    @Min(1000)
    private int sendTimeLimitMs = 10_000;

    @Min(1024)
    private int sendBufferSizeBytes = 256 * 1024;

    @Min(1000)
    private long idleTimeoutMs = 75_000;

    @Min(1)
    private int maxTextFrameBytes = 1024;

    @Min(1000)
    private long asyncSendTimeoutMs = 10_000;

    @Min(1)
    @Max(64)
    private int dispatchWorkers = Math.min(Runtime.getRuntime().availableProcessors(), 8);

    @Min(1)
    private int dispatchQueueCapacity = 10_000;

    @Min(1000)
    private long terminalGraceMs = 45_000;

    @Min(1000)
    private long panelAuthorizationSweepIntervalMs = 30_000;

    @Min(1)
    private int maxUnauthenticatedConnections = 512;

    @Min(1)
    private int maxUnauthenticatedConnectionsPerIp = 32;
}
