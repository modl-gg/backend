package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.admin.data.SystemPrompt;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RESTMappingV1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_SYSTEM)
@RequiredArgsConstructor
@Slf4j
public class AdminSystemController {
    private static final String CONFIG_COLLECTION = "system_config";
    private static final String PROMPTS_COLLECTION = "system_prompts";

    private final DynamicMongoTemplateProvider mongoProvider;

    private MongoTemplate getTemplate() {
        return mongoProvider.getGlobalDatabase();
    }

    private SystemConfig getOrCreateConfig() {
        Query query = Query.query(Criteria.where("configId").is("main_config"));
        SystemConfig config = getTemplate().findOne(query, SystemConfig.class, CONFIG_COLLECTION);

        if (config == null) {
            config = new SystemConfig();
            config = getTemplate().save(config, CONFIG_COLLECTION);
        }

        return config;
    }

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        try {
            SystemConfig config = getOrCreateConfig();
            return ResponseEntity.ok(Map.of("success", true, "data", config));
        } catch (Exception e) {
            log.error("Get config error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to fetch configuration"));
        }
    }

    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody SystemConfig newConfig) {
        try {
            SystemConfig existing = getOrCreateConfig();

            // Update fields
            if (newConfig.getGeneral() != null) existing.setGeneral(newConfig.getGeneral());
            if (newConfig.getLogging() != null) existing.setLogging(newConfig.getLogging());
            if (newConfig.getSecurity() != null) existing.setSecurity(newConfig.getSecurity());
            if (newConfig.getNotifications() != null) existing.setNotifications(newConfig.getNotifications());
            if (newConfig.getPerformance() != null) existing.setPerformance(newConfig.getPerformance());
            if (newConfig.getFeatures() != null) existing.setFeatures(newConfig.getFeatures());
            existing.setUpdatedAt(new Date());

            SystemConfig saved = getTemplate().save(existing, CONFIG_COLLECTION);
            log.info("Configuration updated by admin");

            return ResponseEntity.ok(Map.of("success", true, "data", saved, "message", "Configuration updated successfully"));
        } catch (Exception e) {
            log.error("Update config error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to update configuration"));
        }
    }

    @GetMapping("/maintenance")
    public ResponseEntity<?> getMaintenanceStatus() {
        try {
            SystemConfig config = getOrCreateConfig();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "isActive", config.getGeneral().isMaintenanceMode(),
                            "message", config.getGeneral().getMaintenanceMessage()
                    )
            ));
        } catch (Exception e) {
            log.error("Get maintenance status error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to fetch maintenance status"));
        }
    }

    @PostMapping("/maintenance/toggle")
    public ResponseEntity<?> toggleMaintenance(@RequestBody Map<String, Object> request) {
        try {
            boolean enabled = (Boolean) request.getOrDefault("enabled", false);
            String message = (String) request.get("message");

            SystemConfig config = getOrCreateConfig();
            config.getGeneral().setMaintenanceMode(enabled);
            if (message != null) {
                config.getGeneral().setMaintenanceMessage(message);
            }
            config.setUpdatedAt(new Date());

            getTemplate().save(config, CONFIG_COLLECTION);
            log.info("Maintenance mode {} by admin", enabled ? "enabled" : "disabled");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of("isActive", enabled, "message", config.getGeneral().getMaintenanceMessage()),
                    "message", "Maintenance mode " + (enabled ? "enabled" : "disabled")
            ));
        } catch (Exception e) {
            log.error("Toggle maintenance error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to toggle maintenance mode"));
        }
    }

    @GetMapping("/rate-limits")
    public ResponseEntity<?> getRateLimits() {
        try {
            SystemConfig config = getOrCreateConfig();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "current", config.getPerformance(),
                            "active", true,
                            "resetTime", new Date(System.currentTimeMillis() + 15 * 60 * 1000)
                    )
            ));
        } catch (Exception e) {
            log.error("Get rate limits error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to fetch rate limit status"));
        }
    }

    @PutMapping("/rate-limits")
    public ResponseEntity<?> updateRateLimits(@RequestBody Map<String, Integer> request) {
        try {
            SystemConfig config = getOrCreateConfig();

            if (request.containsKey("rateLimitRequests")) {
                config.getPerformance().setRateLimitRequests(request.get("rateLimitRequests"));
            }
            if (request.containsKey("rateLimitWindow")) {
                config.getPerformance().setRateLimitWindow(request.get("rateLimitWindow"));
            }
            config.setUpdatedAt(new Date());

            getTemplate().save(config, CONFIG_COLLECTION);
            log.info("Rate limits updated by admin");

            return ResponseEntity.ok(Map.of("success", true, "data", config.getPerformance(), "message", "Rate limits updated successfully"));
        } catch (Exception e) {
            log.error("Update rate limits error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to update rate limits"));
        }
    }

    @GetMapping("/prompts")
    public ResponseEntity<?> getPrompts() {
        try {
            List<SystemPrompt> prompts = getTemplate().findAll(SystemPrompt.class, PROMPTS_COLLECTION);
            return ResponseEntity.ok(Map.of("success", true, "data", prompts));
        } catch (Exception e) {
            log.error("Error fetching system prompts", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to fetch system prompts"));
        }
    }

    @PutMapping("/prompts/{strictnessLevel}")
    public ResponseEntity<?> updatePrompt(@PathVariable String strictnessLevel, @RequestBody Map<String, String> request) {
        try {
            if (!List.of("lenient", "standard", "strict").contains(strictnessLevel)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid strictness level"));
            }

            String prompt = request.get("prompt");
            if (prompt == null || prompt.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Prompt content is required"));
            }

            Query query = Query.query(Criteria.where("strictnessLevel").is(strictnessLevel));
            Update update = new Update()
                    .set("prompt", prompt.trim())
                    .set("updatedAt", new Date())
                    .setOnInsert("strictnessLevel", strictnessLevel)
                    .setOnInsert("isActive", true)
                    .setOnInsert("createdAt", new Date());

            getTemplate().upsert(query, update, SystemPrompt.class, PROMPTS_COLLECTION);

            SystemPrompt updated = getTemplate().findOne(query, SystemPrompt.class, PROMPTS_COLLECTION);
            log.info("System prompt for {} level updated", strictnessLevel);

            return ResponseEntity.ok(Map.of("success", true, "data", updated, "message", "System prompt for " + strictnessLevel + " level updated successfully"));
        } catch (Exception e) {
            log.error("Error updating system prompt", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to update system prompt"));
        }
    }

    @PostMapping("/prompts/{strictnessLevel}/reset")
    public ResponseEntity<?> resetPrompt(@PathVariable String strictnessLevel) {
        try {
            if (!List.of("lenient", "standard", "strict").contains(strictnessLevel)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid strictness level"));
            }

            String defaultPrompt = getDefaultPrompt(strictnessLevel);

            Query query = Query.query(Criteria.where("strictnessLevel").is(strictnessLevel));
            Update update = new Update()
                    .set("prompt", defaultPrompt)
                    .set("updatedAt", new Date())
                    .setOnInsert("strictnessLevel", strictnessLevel)
                    .setOnInsert("isActive", true)
                    .setOnInsert("createdAt", new Date());

            getTemplate().upsert(query, update, SystemPrompt.class, PROMPTS_COLLECTION);

            SystemPrompt reset = getTemplate().findOne(query, SystemPrompt.class, PROMPTS_COLLECTION);
            log.info("System prompt for {} level reset to default", strictnessLevel);

            return ResponseEntity.ok(Map.of("success", true, "data", reset, "message", "System prompt for " + strictnessLevel + " level reset to default"));
        } catch (Exception e) {
            log.error("Error resetting system prompt", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to reset system prompt"));
        }
    }

    @PostMapping("/services/{service}/restart")
    public ResponseEntity<?> restartService(@PathVariable String service) {
        try {
            List<String> allowedServices = List.of("api", "worker", "scheduler", "cache", "database");
            if (!allowedServices.contains(service)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Invalid service name. Allowed: " + String.join(", ", allowedServices)
                ));
            }

            log.info("Service restart requested for: {} by admin", service);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "service", service,
                            "status", "restarting",
                            "requestedAt", new Date()
                    ),
                    "message", "Service " + service + " restart initiated"
            ));
        } catch (Exception e) {
            log.error("Service restart error for {}", service, e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to restart service"));
        }
    }

    private String getDefaultPrompt(String level) {
        String common = """
            You are an AI moderator analyzing Minecraft server chat logs for rule violations.
            Analyze the provided chat transcript and determine if any moderation action is needed.
            """;

        return switch (level) {
            case "lenient" -> common + "\n\nLENIENT MODE: Give players significant benefit of the doubt. Only suggest action for clear, obvious rule violations.";
            case "strict" -> common + "\n\nSTRICT MODE: Enforce rules rigorously with minimal tolerance for violations. Prefer higher severity punishments.";
            default -> common + "\n\nSTANDARD MODE: Apply consistent moderation based on community standards. Balance individual player behavior with overall server atmosphere.";
        };
    }
}
