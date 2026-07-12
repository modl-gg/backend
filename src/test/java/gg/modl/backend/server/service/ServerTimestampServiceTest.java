package gg.modl.backend.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import gg.modl.backend.database.mongo.repository.ServerSettingsTimestampRepository;
import gg.modl.backend.realtime.dispatch.RealtimeEventDispatcher;
import gg.modl.backend.realtime.dispatch.RealtimeOutboundEvent;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.proto.modl.v1.RealtimeEnvelope;
import gg.modl.proto.modl.v1.Topic;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServerTimestampServiceTest {
    @Mock
    private ServerSettingsTimestampRepository serverRepository;

    @Mock
    private RealtimeEventDispatcher realtimeEventDispatcher;

    @Test
    void updateStaffPermissionsTimestampPublishesPermissionInvalidation() {
        ServerTimestampService service = new ServerTimestampService(serverRepository, realtimeEventDispatcher);
        Server server = server("server-1");

        service.updateStaffPermissionsTimestamp(server);

        Date timestamp = staffPermissionsTimestamp();
        RealtimeOutboundEvent event = publishedEvent();
        assertEquals("server-1", event.serverId());
        assertEquals(Topic.TOPIC_MINECRAFT_PERMISSIONS, event.topic());
        assertEquals("server-1::staffPermissionsUpdatedAt::" + timestamp.toInstant(), event.envelope().getEventId());
        assertEquals(RealtimeEnvelope.PayloadCase.PERMISSION_INVALIDATED, event.envelope().getPayloadCase());
    }

    @Test
    void updatePunishmentTypesTimestampPublishesPunishmentTypeInvalidation() {
        ServerTimestampService service = new ServerTimestampService(serverRepository, realtimeEventDispatcher);
        Server server = server("server-2");

        service.updatePunishmentTypesTimestamp(server);

        Date timestamp = punishmentTypesTimestamp();
        RealtimeOutboundEvent event = publishedEvent();
        assertEquals("server-2", event.serverId());
        assertEquals(Topic.TOPIC_MINECRAFT_PUNISHMENT_TYPES, event.topic());
        assertEquals("server-2::punishmentTypesUpdatedAt::" + timestamp.toInstant(), event.envelope().getEventId());
        assertEquals(RealtimeEnvelope.PayloadCase.PUNISHMENT_TYPE_INVALIDATED, event.envelope().getPayloadCase());
    }

    @Test
    void updateStaffPermissionsTimestampPublishesDistinctEventIdsForSeparateTimestamps() {
        ServerTimestampService service = new ServerTimestampService(serverRepository, realtimeEventDispatcher);
        Server server = server("server-4");

        service.updateStaffPermissionsTimestamp(server);
        service.updateStaffPermissionsTimestamp(server);

        ArgumentCaptor<Date> timestampCaptor = ArgumentCaptor.forClass(Date.class);
        verify(serverRepository, times(2)).updateStaffPermissionsTimestamp(eq("server-4"), timestampCaptor.capture());
        ArgumentCaptor<RealtimeOutboundEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeOutboundEvent.class);
        verify(realtimeEventDispatcher, times(2)).publish(eventCaptor.capture());

        assertEquals(
            "server-4::staffPermissionsUpdatedAt::" + timestampCaptor.getAllValues().get(0).toInstant(),
            eventCaptor.getAllValues().get(0).envelope().getEventId()
        );
        assertEquals(
            "server-4::staffPermissionsUpdatedAt::" + timestampCaptor.getAllValues().get(1).toInstant(),
            eventCaptor.getAllValues().get(1).envelope().getEventId()
        );
        assertNotEquals(
            eventCaptor.getAllValues().get(0).envelope().getEventId(),
            eventCaptor.getAllValues().get(1).envelope().getEventId()
        );
    }

    @Test
    void updatePunishmentTypesTimestampPublishesDistinctEventIdsForSeparateTimestamps() {
        ServerTimestampService service = new ServerTimestampService(serverRepository, realtimeEventDispatcher);
        Server server = server("server-5");

        service.updatePunishmentTypesTimestamp(server);
        service.updatePunishmentTypesTimestamp(server);

        ArgumentCaptor<Date> timestampCaptor = ArgumentCaptor.forClass(Date.class);
        verify(serverRepository, times(2)).updatePunishmentTypesTimestamp(eq("server-5"), timestampCaptor.capture());
        ArgumentCaptor<RealtimeOutboundEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeOutboundEvent.class);
        verify(realtimeEventDispatcher, times(2)).publish(eventCaptor.capture());

        assertEquals(
            "server-5::punishmentTypesUpdatedAt::" + timestampCaptor.getAllValues().get(0).toInstant(),
            eventCaptor.getAllValues().get(0).envelope().getEventId()
        );
        assertEquals(
            "server-5::punishmentTypesUpdatedAt::" + timestampCaptor.getAllValues().get(1).toInstant(),
            eventCaptor.getAllValues().get(1).envelope().getEventId()
        );
        assertNotEquals(
            eventCaptor.getAllValues().get(0).envelope().getEventId(),
            eventCaptor.getAllValues().get(1).envelope().getEventId()
        );
    }

    @Test
    void updateStaffPermissionsTimestampDoesNotPropagateDispatchFailure() {
        ServerTimestampService service = new ServerTimestampService(serverRepository, realtimeEventDispatcher);
        Server server = server("server-3");
        doThrow(new RuntimeException("dispatch failed")).when(realtimeEventDispatcher).publish(any(RealtimeOutboundEvent.class));

        assertDoesNotThrow(() -> service.updateStaffPermissionsTimestamp(server));

        verify(serverRepository).updateStaffPermissionsTimestamp(eq("server-3"), any(Date.class));
    }

    private RealtimeOutboundEvent publishedEvent() {
        ArgumentCaptor<RealtimeOutboundEvent> captor = ArgumentCaptor.forClass(RealtimeOutboundEvent.class);
        verify(realtimeEventDispatcher).publish(captor.capture());
        return captor.getValue();
    }

    private Date staffPermissionsTimestamp() {
        ArgumentCaptor<Date> captor = ArgumentCaptor.forClass(Date.class);
        verify(serverRepository).updateStaffPermissionsTimestamp(eq("server-1"), captor.capture());
        return captor.getValue();
    }

    private Date punishmentTypesTimestamp() {
        ArgumentCaptor<Date> captor = ArgumentCaptor.forClass(Date.class);
        verify(serverRepository).updatePunishmentTypesTimestamp(eq("server-2"), captor.capture());
        return captor.getValue();
    }

    private Server server(String id) {
        Server server = new Server("Demo", "demo", "demo_db", "admin@example.com", true, ServerPlan.FREE);
        server.setId(id);
        return server;
    }
}
