package gg.modl.backend.realtime.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public final class RealtimeEventCounters {
    private static final String EVENTS_COUNTER = "modl.realtime.events";

    private RealtimeEventCounters() {
    }

    public static double realtimeEventCount(MeterRegistry registry, String event, String... extraTags) {
        return registry.find(EVENTS_COUNTER)
            .tag("event", event)
            .tags(extraTags)
            .counters()
            .stream()
            .mapToDouble(Counter::count)
            .sum();
    }
}
