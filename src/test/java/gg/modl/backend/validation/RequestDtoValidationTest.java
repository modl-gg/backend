package gg.modl.backend.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.modl.backend.admin.dto.request.CreateSystemLogRequest;
import gg.modl.backend.admin.dto.request.UpdateSystemConfigRequest;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import gg.modl.backend.settings.dto.request.PunishmentTypeRequest;
import gg.modl.backend.settings.dto.request.UpdateWebhookSettingsRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RequestDtoValidationTest {
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void createSystemLogRequestRejectsInvalidLevel() {
        CreateSystemLogRequest request = new CreateSystemLogRequest(
            "fatal",
            "message",
            "monitor",
            "infra",
            "server-1",
            Map.of("cpu", 95)
        );

        assertHasViolation(validator.validate(request), "level");
    }

    private void assertHasViolation(Set<? extends ConstraintViolation<?>> violations, String propertyPath) {
        assertFalse(violations.isEmpty(), "Expected validation to fail");
        assertTrue(
            violations.stream().anyMatch(violation -> propertyPath.equals(violation.getPropertyPath().toString())),
            () -> "Expected violation for property '" + propertyPath + "' but got " +
                  violations.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).toList()
        );
    }

    @Test
    void updateSystemConfigRequestRejectsOutOfRangePerformanceValues() {
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
            null,
            null,
            null,
            null,
            new UpdateSystemConfigRequest.PerformanceConfigRequest(
                null,
                RequestValidationLimits.RATE_LIMIT_REQUESTS_MAX + 1,
                null,
                null,
                null,
                null
            ),
            null
        );

        assertHasViolation(validator.validate(request), "performance.rateLimitRequests");
    }

    @Test
    void punishmentTypeRequestRejectsInvalidCategory() {
        PunishmentTypeRequest request = new PunishmentTypeRequest(
            "Spam",
            "Other",
            null,
            null,
            1,
            null,
            1,
            "Staff desc",
            "Player desc",
            false,
            false,
            false,
            true,
            null,
            false,
            false
        );

        assertHasViolation(validator.validate(request), "category");
    }

    @Test
    void webhookSettingsRequestRejectsTooManyEmbedFields() {
        List<UpdateWebhookSettingsRequest.EmbedFieldRequest> fields = new ArrayList<>();
        for (int i = 0; i < RequestValidationLimits.EMBED_FIELDS_MAX_ENTRIES + 1; i++) {
            fields.add(new UpdateWebhookSettingsRequest.EmbedFieldRequest("Field " + i, "Value " + i, true));
        }

        UpdateWebhookSettingsRequest request = new UpdateWebhookSettingsRequest(
            "https://discord.com/api/webhooks/123/token",
            "123456789012345678",
            "modl Panel",
            "https://example.com/avatar.png",
            true,
            new UpdateWebhookSettingsRequest.NotificationSettingsRequest(true, true, true),
            new UpdateWebhookSettingsRequest.EmbedTemplatesRequest(
                new UpdateWebhookSettingsRequest.EmbedTemplateRequest("Title", "Description", "#3498db", fields),
                null,
                null
            )
        );

        assertHasViolation(validator.validate(request), "embedTemplates.newTickets.fields");
    }
}
