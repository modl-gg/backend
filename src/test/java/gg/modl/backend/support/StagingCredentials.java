package gg.modl.backend.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class StagingCredentials {

    private static final Map<String, String> PROPS = loadProperties();

    private StagingCredentials() {}

    public static String baseUrl()       { return get("MODL_BASE_URL"); }
    public static String apiKey()        { return get("MODL_API_KEY"); }
    public static String sessionToken()  { return get("MODL_SESSION_TOKEN"); }
    public static String serverDomain()  { return get("MODL_SERVER_DOMAIN"); }
    public static String mongoUri()      { return get("MODL_MONGO_URI"); }
    public static String panelOrigin()   { return get("MODL_PANEL_ORIGIN"); }

    public static boolean isAvailable() {
        return baseUrl() != null && apiKey() != null && sessionToken() != null && serverDomain() != null;
    }

    private static String get(String key) {
        String val = PROPS.get(key);
        if (val != null) return val;
        return System.getenv(key);
    }

    private static Map<String, String> loadProperties() {
        Map<String, String> map = new HashMap<>();
        Path envFile = Path.of("").toAbsolutePath().resolve(".env.test");
        if (!Files.exists(envFile)) {
            // Try one level up (in case tests run from backend/)
            envFile = envFile.getParent().resolve(".env.test");
        }
        if (Files.exists(envFile)) {
            try {
                for (String line : Files.readAllLines(envFile)) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                    }
                }
            } catch (IOException e) {
                // ignore – fall back to env vars
            }
        }
        return map;
    }
}
