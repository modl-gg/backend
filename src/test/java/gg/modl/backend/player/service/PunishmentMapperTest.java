package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentNote;
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

class PunishmentMapperTest {

    private PlayerStatusCalculator statusCalculator;
    private List<PunishmentType> types;

    @BeforeEach
    void setUp() {
        statusCalculator = new PlayerStatusCalculator(
            mock(PunishmentTypeService.class),
            mock(OffenderThresholdSettingsService.class)
        );
        types = List.of(PunishmentType.builder()
            .ordinal(6)
            .name("Chat Abuse")
            .category("Social")
            .build());
    }

    @Test
    void customTypeUsesDataReasonForDescription() {
        Map<String, Object> data = new HashMap<>();
        data.put("reason", "spam in chat");
        data.put("duration", 3600_000L);
        Punishment punishment = punishment(6, List.of(autoNote()), data);

        Map<String, Object> simple = PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, Map.of());

        assertEquals("spam in chat", simple.get("description"));
    }

    @Test
    void customTypeFallsBackToRealNoteWhenNoDataReason() {
        Map<String, Object> data = new HashMap<>();
        data.put("duration", 3600_000L);
        Punishment punishment = punishment(6, List.of(autoNote(), note("used slurs")), data);

        Map<String, Object> simple = PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, Map.of());

        assertEquals("used slurs", simple.get("description"));
    }

    @Test
    void customTypeWithOnlyAutoNoteAndNoReasonGetsDefault() {
        Map<String, Object> data = new HashMap<>();
        data.put("duration", 3600_000L);
        Punishment punishment = punishment(6, List.of(autoNote()), data);

        Map<String, Object> simple = PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, Map.of());

        assertEquals("No reason specified", simple.get("description"));
    }

    private PunishmentNote autoNote() {
        return note("issued 1d mute");
    }

    private PunishmentNote note(String text) {
        return new PunishmentNote("note-" + text.hashCode(), text, new Date(), "Mod", null);
    }

    private Punishment punishment(int typeOrdinal, List<PunishmentNote> notes, Map<String, Object> data) {
        return new Punishment(
            "p-1",
            typeOrdinal,
            "Mod",
            null,
            new Date(),
            new Date(),
            new ArrayList<>(),
            new ArrayList<>(notes),
            new ArrayList<>(),
            new ArrayList<>(),
            data
        );
    }
}
