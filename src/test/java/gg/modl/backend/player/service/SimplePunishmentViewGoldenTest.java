package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.player.controller.MinecraftPlayerProtoMapper;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentDataView;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.player.dto.response.SimplePunishmentView;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeIndex;
import gg.modl.proto.modl.v1.SimplePunishment;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimplePunishmentViewGoldenTest {

    private static final ObjectMapper JSON = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private PlayerStatusCalculator statusCalculator;

    @BeforeEach
    void setUp() {
        statusCalculator = mock(PlayerStatusCalculator.class);
    }

    @Test
    void fullyPopulatedViewSerializesLikeLegacyMapAndMapsToProto() throws Exception {
        PunishmentType type = PunishmentType.builder()
            .ordinal(6)
            .name("Chat Abuse")
            .category("Social")
            .playerDescription("Watch your language")
            .build();
        List<PunishmentType> types = List.of(type);

        Map<String, Object> data = new HashMap<>();
        data.put("reason", "spam in chat");
        PunishmentModification pardon = new PunishmentModification(
            "mod-1", "MANUAL_PARDON", new Date(1_650_000_000_000L), "Admin", null,
            "cleared", 3_600_000L, null, null);
        Punishment punishment = punishment(6, "Mod", null, List.of(pardon), List.of(autoNote()), data);

        when(statusCalculator.getEffectiveExpiry(punishment)).thenReturn(new Date(1_800_000_000_000L));
        when(statusCalculator.getEffectiveCategory(eq(type), any(PunishmentDataView.class))).thenReturn("MUTE");

        SimplePunishmentView view = PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, Map.of());
        Map<String, Object> legacy = legacyToSimplePunishment(punishment, PunishmentTypeIndex.byOrdinal(types),
            statusCalculator, Map.of());

        assertSameJson(legacy, view);

        SimplePunishment proto = MinecraftPlayerProtoMapper.toSimplePunishment(view);
        assertEquals("Chat Abuse", proto.getType());
        assertEquals("spam in chat", proto.getDescription());
        assertEquals("p-1", proto.getId());
        assertTrue(proto.getStarted());
        assertEquals(6, proto.getOrdinal());
        assertEquals("MUTE", proto.getCategory());
        assertEquals(1_800_000_000_000L, proto.getExpiration());
        assertEquals("Mod", proto.getIssuerName());
        assertEquals(punishment.getIssued().getTime(), proto.getIssuedAt());
        assertEquals("Watch your language", proto.getPlayerDescription());
    }

    @Test
    void minimalViewWithAbsentOptionalsSerializesLikeLegacyMap() throws Exception {
        Map<String, Object> data = new HashMap<>();
        Punishment punishment = punishment(6, "Mod", null, List.of(), List.of(autoNote()), data);

        SimplePunishmentView view = PunishmentMapper.toSimplePunishment(punishment, List.of(), statusCalculator, Map.of());
        Map<String, Object> legacy = legacyToSimplePunishment(punishment, PunishmentTypeIndex.byOrdinal(List.of()),
            statusCalculator, Map.of());

        assertSameJson(legacy, view);
        assertEquals("OTHER", view.category());
        assertEquals("No reason specified", view.description());

        SimplePunishment proto = MinecraftPlayerProtoMapper.toSimplePunishment(view);
        assertEquals(0L, proto.getExpiration());
        assertEquals("", proto.getPlayerDescription());
        assertEquals("OTHER", proto.getCategory());
    }

    @Test
    void nullToleratedModificationValuesSerializeLikeLegacyMap() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("reason", "griefing");
        PunishmentModification modification = new PunishmentModification(
            "mod-2", "SET_ALT_BLOCKING", null, null, null,
            "toggled", null, null, null);
        Punishment punishment = punishment(6, null, "staff-77", List.of(modification), List.of(), data);

        SimplePunishmentView view = PunishmentMapper.toSimplePunishment(punishment, List.of(), statusCalculator, Map.of());
        Map<String, Object> legacy = legacyToSimplePunishment(punishment, PunishmentTypeIndex.byOrdinal(List.of()),
            statusCalculator, Map.of());

        assertSameJson(legacy, view);
        assertEquals(0L, view.modifications().get(0).effectiveDuration());
        assertEquals("Unknown Staff", view.issuerName());
        assertEquals("Console", view.modifications().get(0).issuerName());
    }

    private void assertSameJson(Map<String, Object> legacy, SimplePunishmentView view) throws Exception {
        String legacyJson = JSON.writeValueAsString(legacy);
        String viewJson = JSON.writeValueAsString(view);
        assertEquals(legacyJson, viewJson);
        assertEquals(JSON.readTree(legacyJson), JSON.readTree(viewJson));
    }

    private static Map<String, Object> legacyToSimplePunishment(
        Punishment punishment,
        Map<Integer, PunishmentType> typesByOrdinal,
        PlayerStatusCalculator statusCalculator,
        Map<String, String> resolvedIssuers
    ) {
        PunishmentDataView data = punishment.data();
        Date expires = statusCalculator.getEffectiveExpiry(punishment);

        PunishmentType punishmentType = typesByOrdinal.get(punishment.getTypeOrdinal());
        String typeName = punishmentType != null ? punishmentType.getName() : "Unknown";
        String playerDescription = punishmentType != null ? punishmentType.getPlayerDescription() : null;

        String effectiveCategory = statusCalculator.getEffectiveCategory(punishmentType, data);
        String category = effectiveCategory != null ? effectiveCategory : "OTHER";

        String reason = data.reason();
        if ((reason == null || reason.isBlank()) && punishment.getNotes() != null) {
            for (PunishmentNote note : punishment.getNotes()) {
                String noteText = note.text();
                if (noteText != null && !PunishmentMapper.isAutoGeneratedNote(noteText)) {
                    reason = noteText;
                    break;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", punishment.getId());
        result.put("type", typeName);
        result.put("category", category);
        result.put("typeOrdinal", punishment.getTypeOrdinal());
        result.put("ordinal", punishment.getTypeOrdinal());
        result.put("started", punishment.getStarted() != null);
        result.put("expiration", expires != null ? expires.getTime() : null);
        result.put("description", reason != null && !reason.isBlank() ? reason : "No reason specified");
        result.put("issuerName", PunishmentMapper.resolveIssuer(punishment.getIssuerId(), punishment.getIssuerName(), resolvedIssuers));
        result.put("issuedAt", punishment.getIssued().getTime());
        result.put("playerDescription", playerDescription);
        result.put("modifications", punishment.getModifications()
            .stream().map(m -> {
                Map<String, Object> modMap = new LinkedHashMap<>();
                modMap.put("type", m.type());
                modMap.put("timestamp", m.date() != null ? m.date().getTime() : null);
                modMap.put("effectiveDuration", m.effectiveDuration() != null ? m.effectiveDuration() : 0L);
                modMap.put("issuerName", PunishmentMapper.resolveIssuer(m.issuerId(), m.issuerName(), resolvedIssuers));
                return modMap;
            }).toList());

        return result;
    }

    private PunishmentNote autoNote() {
        return new PunishmentNote("note-auto", "issued 1d mute", new Date(), "Mod", null);
    }

    private Punishment punishment(
        int typeOrdinal,
        String issuerName,
        String issuerId,
        List<PunishmentModification> modifications,
        List<PunishmentNote> notes,
        Map<String, Object> data
    ) {
        return new Punishment(
            "p-1",
            typeOrdinal,
            issuerName,
            issuerId,
            new Date(1_600_000_000_000L),
            new Date(1_600_000_100_000L),
            new ArrayList<>(modifications),
            new ArrayList<>(notes),
            new ArrayList<>(),
            new ArrayList<>(),
            data
        );
    }
}
