package gg.modl.backend.realtime.schedule;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import gg.modl.backend.realtime.config.RealtimeProperties;
import gg.modl.backend.realtime.state.RealtimeConnectionRegistry;
import gg.modl.backend.realtime.transport.RealtimeCodec;
import gg.modl.backend.realtime.transport.RealtimeSessionOperations;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * The emitter carries a second, package-private constructor for injecting a delivery executor in
 * tests. Two candidate constructors make Spring's autowiring ambiguous, and it then falls back to a
 * no-arg constructor that does not exist — failing at context startup rather than at compile time,
 * where no other test would catch it. This pins the annotated constructor as the injection point.
 */
class RealtimeServerHeartbeatEmitterWiringTest {

    @Test
    void springResolvesTheAnnotatedConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RealtimeProperties.class, RealtimeProperties::new);
            context.registerBean(RealtimeConnectionRegistry.class, () -> mock(RealtimeConnectionRegistry.class));
            context.registerBean(RealtimeCodec.class, () -> mock(RealtimeCodec.class));
            context.registerBean(RealtimeSessionOperations.class, () -> mock(RealtimeSessionOperations.class));
            context.registerBean(RealtimeServerHeartbeatEmitter.class);
            context.refresh();

            assertNotNull(context.getBean(RealtimeServerHeartbeatEmitter.class));
        }
    }
}
