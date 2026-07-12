package gg.modl.backend.player.service;

import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.dto.response.SimplePunishmentView;
import gg.modl.backend.player.dto.response.SyncPunishmentEntry;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.proto.modl.v1.PanelResource;
import gg.modl.proto.modl.v1.SyncModifiedPunishment;
import gg.modl.proto.modl.v1.SyncPendingPunishment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    private SyncPunishmentEntry toEntry(Server server, Player player, Punishment punishment) {
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        Map<String, String> resolvedIssuers = issuerNameResolver.resolveForPunishments(server, List.of(punishment));
        SimplePunishmentView simplePunishment =
            PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, resolvedIssuers);
        return new SyncPunishmentEntry(
            player.getMinecraftUuid().toString(),
            PlayerDataUtils.extractLatestUsername(player.getUsernames()),
            simplePunishment
        );
    }

    private SyncPunishmentEntry toEntry(Server server, EntryPunishment change) {
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        Map<String, String> resolvedIssuers = issuerNameResolver.resolveForPunishments(server, List.of(change.punishment()));
        SimplePunishmentView simplePunishment =
            PunishmentMapper.toSimplePunishment(change.punishment(), types, statusCalculator, resolvedIssuers);
        return new SyncPunishmentEntry(
            change.minecraftUuid(),
            change.username(),
            simplePunishment
        );
    }

    public record PlayerPunishment(Player player, Punishment punishment) {
    }

    public record EntryPunishment(String minecraftUuid, String username, Punishment punishment) {
    }
}
