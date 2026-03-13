package gg.modl.backend.migration.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "modl.migration")
@Validated
@Getter
@Setter
public class MigrationConfiguration {
    private String uploadDir = "uploads/migrations";
}
