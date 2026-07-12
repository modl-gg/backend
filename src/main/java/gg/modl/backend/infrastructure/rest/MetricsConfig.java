package gg.modl.backend.infrastructure.rest;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterFilter uriTagCardinalityFilter() {
        return MeterFilter.maximumAllowableTags("http.server.requests", "uri", 200, MeterFilter.deny());
    }

    @Bean
    public MeterFilter exceptionTagCardinalityFilter() {
        return MeterFilter.maximumAllowableTags("http.server.requests", "exception", 50, MeterFilter.deny());
    }
}
