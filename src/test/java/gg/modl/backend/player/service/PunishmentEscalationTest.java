package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.DurationDetail;
import gg.modl.backend.settings.data.OffenderThresholdSettings;
import gg.modl.backend.settings.data.OffenseLevelDurations;
import gg.modl.backend.settings.data.PunishmentDurations;
import gg.modl.backend.settings.data.PunishmentPoints;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.OffenderThresholdSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeService;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PunishmentEscalationTest {

    private static final int CHEATING_ORDINAL = 14;
    private static final long SECOND_MS = 1000L;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final long MONTH_MS = 30L * DAY_MS;

    private static final long FIRST_DURATION_MS = 7L * SECOND_MS;
    private static final long MEDIUM_DURATION_MS = 14L * SECOND_MS;
    private static final long HABITUAL_PERMANENT_MS = -1L;

    private Server server;
    private PunishmentDurationCalculator durationCalculator;

    @BeforeEach
    void setUp() {
        server = mock(Server.class);
        PunishmentTypeService punishmentTypeService = mock(PunishmentTypeService.class);
        OffenderThresholdSettingsService thresholdService = mock(OffenderThresholdSettingsService.class);

        when(punishmentTypeService.getPunishmentTypes(any())).thenReturn(List.of(cheatingType()));
        when(thresholdService.getThresholdSettings(any())).thenReturn(sixMonthWindowThresholds());

        PlayerStatusCalculator statusCalculator = new PlayerStatusCalculator(punishmentTypeService, thresholdService);
        durationCalculator = new PunishmentDurationCalculator(punishmentTypeService, thresholdService, statusCalculator);
    }

    @Test
    void firstOffenseUsesFirstDuration() {
        PunishmentDurationCalculator.DurationResult result =
            durationCalculator.calculate(server, List.of(), CHEATING_ORDINAL, "lenient");

        assertEquals("first", result.offenseLevel());
        assertEquals(FIRST_DURATION_MS, result.duration().longValue());
        assertEquals("low", result.status());
    }

    @Test
    void expiredPriorOffenseStillEscalatesToMedium() {
        List<Punishment> priorHistory = List.of(expiredLenientBan());

        PunishmentDurationCalculator.DurationResult result =
            durationCalculator.calculate(server, priorHistory, CHEATING_ORDINAL, "lenient");

        assertEquals("medium", result.offenseLevel());
        assertEquals(MEDIUM_DURATION_MS, result.duration().longValue());
    }

    @Test
    void enoughExpiredPriorOffensesEscalateToHabitual() {
        List<Punishment> priorHistory = List.of(expiredLenientBan(), expiredLenientBan(), expiredLenientBan());

        PunishmentDurationCalculator.DurationResult result =
            durationCalculator.calculate(server, priorHistory, CHEATING_ORDINAL, "lenient");

        assertEquals("habitual", result.offenseLevel());
        assertEquals(HABITUAL_PERMANENT_MS, result.duration().longValue());
    }

    @Test
    void pointsOutsideExpiryWindowDoNotEscalate() {
        Punishment beyondWindow = lenientBan(new Date(nowMinus(7L * MONTH_MS)), FIRST_DURATION_MS, null, List.of());

        PunishmentDurationCalculator.DurationResult result =
            durationCalculator.calculate(server, List.of(beyondWindow), CHEATING_ORDINAL, "lenient");

        assertEquals("first", result.offenseLevel());
        assertEquals(FIRST_DURATION_MS, result.duration().longValue());
    }

    @Test
    void permanentPriorOffenseAlwaysCounts() {
        Punishment ancientPermanent = lenientBan(new Date(nowMinus(120L * MONTH_MS)), HABITUAL_PERMANENT_MS, null, List.of());

        PunishmentDurationCalculator.DurationResult result =
            durationCalculator.calculate(server, List.of(ancientPermanent), CHEATING_ORDINAL, "lenient");

        assertEquals("medium", result.offenseLevel());
        assertEquals(MEDIUM_DURATION_MS, result.duration().longValue());
    }

    @Test
    void pardonedPriorOffenseDoesNotCount() {
        PunishmentModification pardon = new PunishmentModification(
            "mod-1", "MANUAL_PARDON", new Date(), "Staff", null, "", null, null, null);
        Punishment pardoned = lenientBan(new Date(nowMinus(10L * 60L * SECOND_MS)), FIRST_DURATION_MS, null, List.of(pardon));

        PunishmentDurationCalculator.DurationResult result =
            durationCalculator.calculate(server, List.of(pardoned), CHEATING_ORDINAL, "lenient");

        assertEquals("first", result.offenseLevel());
        assertEquals(FIRST_DURATION_MS, result.duration().longValue());
    }

    @Test
    void unstartedPriorOffenseDoesNotCount() {
        Punishment unstarted = lenientBan(
            new Date(nowMinus(10L * 60L * SECOND_MS)), FIRST_DURATION_MS, PunishmentStatus.UNSTARTED, List.of());

        PunishmentDurationCalculator.DurationResult result =
            durationCalculator.calculate(server, List.of(unstarted), CHEATING_ORDINAL, "lenient");

        assertEquals("first", result.offenseLevel());
        assertEquals(FIRST_DURATION_MS, result.duration().longValue());
    }

    @Test
    void activePriorOffenseCounts() {
        Punishment stillActive = lenientBan(new Date(nowMinus(SECOND_MS)), 60L * 60L * 1000L, null, List.of());

        PunishmentDurationCalculator.DurationResult result =
            durationCalculator.calculate(server, List.of(stillActive), CHEATING_ORDINAL, "lenient");

        assertEquals("medium", result.offenseLevel());
        assertEquals(MEDIUM_DURATION_MS, result.duration().longValue());
    }

    @Test
    void kickPriorOffenseDoesNotCount() {
        Map<String, Object> data = new HashMap<>();
        data.put("duration", 0L);
        Punishment kick = new Punishment(
            "kick-1", 0, "Issuer", null,
            new Date(), new Date(nowMinus(10L * 60L * SECOND_MS)), List.of(),
            List.of(), List.of(), List.of(), data);

        PunishmentDurationCalculator.DurationResult result =
            durationCalculator.calculate(server, List.of(kick), CHEATING_ORDINAL, "lenient");

        assertEquals("first", result.offenseLevel());
        assertEquals(FIRST_DURATION_MS, result.duration().longValue());
    }

    private PunishmentType cheatingType() {
        OffenseLevelDurations lenientDurations = new OffenseLevelDurations(
            new DurationDetail(7, "seconds", "ban"),
            new DurationDetail(14, "seconds", "ban"),
            new DurationDetail(14, "days", "permanent ban"));
        return PunishmentType.builder()
            .ordinal(CHEATING_ORDINAL)
            .name("Cheating")
            .category("Gameplay")
            .points(new PunishmentPoints(4, 6, 10))
            .durations(new PunishmentDurations(lenientDurations, lenientDurations, lenientDurations))
            .build();
    }

    private OffenderThresholdSettings sixMonthWindowThresholds() {
        return OffenderThresholdSettings.builder()
            .gameplay(new OffenderThresholdSettings.CategoryThresholds(4, 12, 6))
            .social(new OffenderThresholdSettings.CategoryThresholds(4, 8, 6))
            .build();
    }

    private Punishment expiredLenientBan() {
        return lenientBan(new Date(nowMinus(10L * 60L * SECOND_MS)), FIRST_DURATION_MS, null, List.of());
    }

    private Punishment lenientBan(Date started, long durationMs, String status, List<PunishmentModification> modifications) {
        Map<String, Object> data = new HashMap<>();
        data.put("duration", durationMs);
        data.put("severity", "lenient");
        if (status != null) {
            data.put("status", status);
        }
        return new Punishment(
            "punishment-" + started.getTime() + "-" + durationMs,
            CHEATING_ORDINAL, "Issuer", null,
            new Date(), started, new ArrayList<>(modifications),
            List.of(), List.of(), List.of(), data);
    }

    private long nowMinus(long millis) {
        return System.currentTimeMillis() - millis;
    }
}
