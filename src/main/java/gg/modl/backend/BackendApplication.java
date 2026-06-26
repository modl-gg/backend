package gg.modl.backend;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BackendApplication {
    public static void main(String[] args) {
        loadDotenvIntoSystemProperties();

        SpringApplication.run(BackendApplication.class, args);
    }

    private static void loadDotenvIntoSystemProperties() {
        try {
            final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            dotenv.entries(Dotenv.Filter.DECLARED_IN_ENV_FILE).forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
            );
        } catch (DotenvException e) {
            System.err.println("[BackendApplication] Could not read .env file (it may be a "
                + "directory or unreadable, e.g. an empty bind-mount). Continuing with "
                + "environment/-e variables only. Cause: " + e.getMessage());
        }
    }
}
