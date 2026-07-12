package gg.modl.backend.settings.controller;

import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.PanelResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SettingsInvalidationPublisher {
    private final RealtimeEventPublisher realtimeEventPublisher;

    void invalidateSettings(Server server) {
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_SETTINGS);
    }
}
