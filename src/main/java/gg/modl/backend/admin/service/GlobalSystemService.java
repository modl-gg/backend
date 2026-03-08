package gg.modl.backend.admin.service;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.admin.data.SystemPrompt;
import gg.modl.backend.admin.dto.request.ToggleMaintenanceRequest;
import gg.modl.backend.admin.dto.request.UpdatePromptRequest;
import gg.modl.backend.admin.dto.request.UpdateRateLimitsRequest;
import gg.modl.backend.admin.dto.request.UpdateSystemConfigRequest;
import gg.modl.backend.ai.service.AITicketAnalysisService;
import gg.modl.backend.database.mongo.repository.SystemConfigMongoRepository;
import gg.modl.backend.database.mongo.repository.SystemPromptMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GlobalSystemService {
    private final SystemConfigMongoRepository systemConfigRepository;
    private final SystemPromptMongoRepository systemPromptRepository;
    private final AITicketAnalysisService ticketAnalysisService;

    public SystemConfig getOrCreateConfig() {
        return systemConfigRepository.findMainConfig()
                .orElseGet(() -> systemConfigRepository.saveEntity(new SystemConfig()));
    }

    public SystemConfig.GeneralConfig getGeneralConfigOrDefault() {
        return systemConfigRepository.findMainConfig()
                .map(SystemConfig::getGeneral)
                .orElseGet(SystemConfig.GeneralConfig::new);
    }

    public SystemConfig updateConfig(UpdateSystemConfigRequest request) {
        SystemConfig existing = getOrCreateConfig();
        request.applyTo(existing);
        existing.setUpdatedAt(new Date());
        return systemConfigRepository.saveEntity(existing);
    }

    public Map<String, Object> getMaintenanceStatus() {
        SystemConfig config = getOrCreateConfig();
        return Map.of(
                "isActive", config.getGeneral().isMaintenanceMode(),
                "message", config.getGeneral().getMaintenanceMessage()
        );
    }

    public Map<String, Object> toggleMaintenance(ToggleMaintenanceRequest request) {
        SystemConfig config = getOrCreateConfig();
        config.getGeneral().setMaintenanceMode(request.enabled());
        if (request.message() != null) {
            config.getGeneral().setMaintenanceMessage(request.message());
        }
        config.setUpdatedAt(new Date());
        SystemConfig saved = systemConfigRepository.saveEntity(config);
        return Map.of(
                "isActive", saved.getGeneral().isMaintenanceMode(),
                "message", saved.getGeneral().getMaintenanceMessage()
        );
    }

    public Map<String, Object> getRateLimitStatus() {
        SystemConfig config = getOrCreateConfig();
        return Map.of(
                "current", config.getPerformance(),
                "active", true,
                "resetTime", new Date(System.currentTimeMillis() + 15 * 60 * 1000)
        );
    }

    public SystemConfig.PerformanceConfig updateRateLimits(UpdateRateLimitsRequest request) {
        SystemConfig config = getOrCreateConfig();

        if (request.rateLimitRequests() != null) {
            config.getPerformance().setRateLimitRequests(request.rateLimitRequests());
        }
        if (request.rateLimitWindow() != null) {
            config.getPerformance().setRateLimitWindow(request.rateLimitWindow());
        }
        config.setUpdatedAt(new Date());

        return systemConfigRepository.saveEntity(config).getPerformance();
    }

    public List<SystemPrompt> getPrompts() {
        return systemPromptRepository.findAll();
    }

    public SystemPrompt updatePrompt(String strictnessLevel, UpdatePromptRequest request) {
        String normalizedStrictnessLevel = normalizeStrictnessLevel(strictnessLevel);
        if (normalizedStrictnessLevel == null) {
            throw new IllegalArgumentException("Invalid strictness level");
        }
        String prompt = request.prompt() != null ? request.prompt().trim() : "";
        if (prompt.isEmpty()) {
            throw new IllegalArgumentException("Prompt content is required");
        }

        return upsertPrompt(normalizedStrictnessLevel, prompt);
    }

    public SystemPrompt resetPrompt(String strictnessLevel) {
        String normalizedStrictnessLevel = normalizeStrictnessLevel(strictnessLevel);
        if (normalizedStrictnessLevel == null) {
            throw new IllegalArgumentException("Invalid strictness level");
        }
        return upsertPrompt(normalizedStrictnessLevel, ticketAnalysisService.getDefaultPrompt(normalizedStrictnessLevel));
    }

    private SystemPrompt upsertPrompt(String strictnessLevel, String prompt) {
        return systemPromptRepository.upsertPrompt(strictnessLevel, prompt, new Date());
    }

    private String normalizeStrictnessLevel(String strictnessLevel) {
        if (strictnessLevel == null || strictnessLevel.isBlank()) {
            return null;
        }

        String normalized = strictnessLevel.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LENIENT", "STANDARD", "STRICT" -> normalized;
            default -> null;
        };
    }
}
