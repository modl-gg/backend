package gg.modl.backend.player.service;

import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.proto.modl.v1.PanelResource;
import gg.modl.proto.modl.v1.SyncModifiedPunishment;
import gg.modl.proto.modl.v1.SyncPendingPunishment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bridges punishment mutations to the realtime layer. It owns the single conversion from a
 * domain {@link Punishment} (plus its owning {@link Player}) into the {@code sync.proto} push
 * messages, reusing the same {@link PunishmentMapper} shape the HTTP baseline sync produces so
 * websocket deltas and reconnect baselines never drift. Punishment services depend on this
 * instead of touching {@link RealtimeEventPublisher} or {@link SyncProtoFactory} directly.
 *
 * <p>Best-effort by contract: the underlying publisher swallows dispatch failures, so a realtime
 * hiccup never fails the originating mutation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PunishmentRealtimePublisher {
    private final RealtimeEventPublisher publisher;
    private final SyncProtoFactory syncProtoFactory;
    private final PunishmentTypeService punishmentTypeService;
    private final PlayerStatusCalculator statusCalculator;
    private final IssuerNameResolver issuerNameResolver;

    public void punishmentIssued(Server server, Player player, Punishment punishment) {
        // Building the push payload (type lookups, issuer resolution) can fail, but realtime is
        // best-effort acceleration on top of the HTTP baseline sync, so it must never fail the mutation.
        guard(() -> {
            invalidatePanelViews(server, player, List.of(punishment));
            publisher.pushPunishments(server, List.of(toPending(server, player, punishment)), List.of());
        });
    }

    public void punishmentsPromoted(Server server, Player player, List<Punishment> promoted) {
        if (promoted.isEmpty()) {
            return;
        }
        guard(() -> {
            List<SyncPendingPunishment> pending = new ArrayList<>(promoted.size());
            for (Punishment punishment : promoted) {
                pending.add(toPending(server, player, punishment));
            }
            invalidatePanelViews(server, player, promoted);
            publisher.pushPunishments(server, pending, List.of());
        });
    }

    public void punishmentModified(Server server, Player player, Punishment punishment) {
        guard(() -> {
            invalidatePanelViews(server, player, List.of(punishment));
            publisher.pushPunishments(server, List.of(), List.of(toModified(server, player, punishment)));
        });
    }

    public void punishmentsModified(Server server, List<PlayerPunishment> changes) {
        if (changes.isEmpty()) {
            return;
        }
        guard(() -> {
            List<SyncModifiedPunishment> modified = new ArrayList<>(changes.size());
            for (PlayerPunishment change : changes) {
                modified.add(toModified(server, change.player(), change.punishment()));
            }
            invalidateAllPanelViews(server);
            publisher.pushPunishments(server, List.of(), modified);
        });
    }

    /**
     * Pushes modified-punishment deltas for callers that only have the player's uuid/username
     * (e.g. the audit bulk-pardon loop, which never materializes a {@link Player}). Emits exactly
     * one {@code pushPunishments(modified)} message (the same shape the single-pardon path already
     * sends and the plugin already consumes) and coarse-invalidates the panel player/punishment
     * views so they live-refresh. Best-effort: any conversion failure is swallowed by {@link #guard}.
     */
    public void punishmentsModifiedByEntry(Server server, List<EntryPunishment> changes) {
        if (changes.isEmpty()) {
            return;
        }
        guard(() -> {
            List<SyncModifiedPunishment> modified = new ArrayList<>(changes.size());
            for (EntryPunishment change : changes) {
                modified.add(syncProtoFactory.toModifiedPunishment(toEntry(server, change)));
            }
            publisher.pushPunishments(server, List.of(), modified);
            invalidateAllPanelViews(server);
        });
    }

    public void punishmentDetailsChanged(Server server, Player player, Punishment punishment) {
        guard(() -> invalidatePanelViews(server, player, List.of(punishment)));
    }

    private void invalidatePanelViews(Server server, Player player, List<Punishment> punishments) {
        for (Punishment punishment : punishments) {
            publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_PUNISHMENTS, punishment.getId());
        }
        publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_PLAYERS, player.getMinecraftUuid().toString());
    }

    private void invalidateAllPanelViews(Server server) {
        publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_PUNISHMENTS);
        publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_PLAYERS);
    }

    private void guard(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Failed to publish punishment realtime event", e);
        }
    }

    private SyncPendingPunishment toPending(Server server, Player player, Punishment punishment) {
        return syncProtoFactory.toPendingPunishment(toEntry(server, player, punishment));
    }

    private SyncModifiedPunishment toModified(Server server, Player player, Punishment punishment) {
        return syncProtoFactory.toModifiedPunishment(toEntry(server, player, punishment));
    }

    private Map<String, Object> toEntry(Server server, Player player, Punishment punishment) {
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        Map<String, String> resolvedIssuers = resolveIssuers(server, punishment);
        Map<String, Object> simplePunishment =
            PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, resolvedIssuers);
        return Map.of(
            "minecraftUuid", player.getMinecraftUuid().toString(),
            "username", PlayerDataUtils.extractLatestUsername(player.getUsernames()),
            "punishment", simplePunishment
        );
    }

    private Map<String, Object> toEntry(Server server, EntryPunishment change) {
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        Map<String, String> resolvedIssuers = resolveIssuers(server, change.punishment());
        Map<String, Object> simplePunishment =
            PunishmentMapper.toSimplePunishment(change.punishment(), types, statusCalculator, resolvedIssuers);
        return Map.of(
            "minecraftUuid", change.minecraftUuid(),
            "username", change.username(),
            "punishment", simplePunishment
        );
    }

    private Map<String, String> resolveIssuers(Server server, Punishment punishment) {
        Set<String> ids = new HashSet<>(PunishmentQueryService.collectIssuerIds(punishment));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return issuerNameResolver.batchResolve(ids, server);
    }

    public record PlayerPunishment(Player player, Punishment punishment) {
    }

    /**
     * A punishment delta keyed by the player's uuid/username instead of a materialized {@link Player},
     * for callers (e.g. audit bulk-pardon) that only have those identifiers. {@code minecraftUuid} and
     * {@code username} must be non-null ({@link Map#of} forbids null values); callers must skip rows
     * with a null uuid so one bad row cannot abort the whole batch push.
     */
    public record EntryPunishment(String minecraftUuid, String username, Punishment punishment) {
    }
}
