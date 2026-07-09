package gg.modl.backend.alert.service;

import gg.modl.backend.alert.data.SystemAlert;
import gg.modl.backend.alert.data.SystemAlertAudience;
import gg.modl.backend.alert.data.SystemAlertSeverity;
import gg.modl.backend.database.mongo.repository.SystemAlertMongoRepository;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemAlertService {
    private final SystemAlertMongoRepository alertRepository;

    public List<SystemAlert> getAllAlerts() {
        return alertRepository.findAllOrdered();
    }

    public List<SystemAlert> getVisibleAlerts(boolean superAdmin, Date now) {
        return alertRepository.findVisible(now).stream()
            .filter(alert -> superAdmin || alert.getAudience() == SystemAlertAudience.ALL_PANEL_USERS)
            .toList();
    }

    public SystemAlert createAlert(
        String message,
        @Nullable SystemAlertSeverity severity,
        @Nullable SystemAlertAudience audience,
        @Nullable Date expiresAt,
        String createdBy
    ) {
        Date now = new Date();
        SystemAlert alert = SystemAlert.builder()
            .message(requireNonBlankMessage(message))
            .severity(severity != null ? severity : SystemAlertSeverity.BASIC)
            .audience(audience != null ? audience : SystemAlertAudience.ALL_PANEL_USERS)
            .expiresAt(expiresAt)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(createdBy)
            .updatedBy(createdBy)
            .build();
        return alertRepository.saveEntity(alert);
    }

    public Optional<SystemAlert> updateAlert(
        String id,
        @Nullable String message,
        @Nullable SystemAlertSeverity severity,
        @Nullable SystemAlertAudience audience,
        boolean expiresAtPresent,
        @Nullable Date expiresAt,
        String updatedBy
    ) {
        String trimmedMessage = message != null ? requireNonBlankMessage(message) : null;
        return alertRepository.updateAlert(
            id,
            trimmedMessage,
            severity,
            audience,
            expiresAtPresent,
            expiresAt,
            new Date(),
            updatedBy
        );
    }

    private static String requireNonBlankMessage(String message) {
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Alert message cannot be blank");
        }
        return trimmed;
    }
}
