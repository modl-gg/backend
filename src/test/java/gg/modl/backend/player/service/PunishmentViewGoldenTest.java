package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentEvidence;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.player.dto.response.PunishmentView;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeIndex;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PunishmentViewGoldenTest {

    private static final ObjectMapper JSON = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final Map<String, String> ISSUERS = Map.of("staff-1", "Resolved Mod");

    @Test
    void fullyPopulatedViewSerializesByteIdenticalToLegacyMap() throws Exception {
        PunishmentType type = PunishmentType.builder().ordinal(3).name("Cheating").build();
        List<PunishmentType> types = List.of(type);
        Punishment punishment = fullyPopulatedPunishment();

        PunishmentView view = PunishmentMapper.toPunishmentView(punishment, types, ISSUERS);
        Map<String, Object> legacy = legacyToPunishmentMap(punishment, PunishmentTypeIndex.byOrdinal(types), ISSUERS);

        assertByteIdentical(legacy, view);
    }

    @Test
    void minimalViewWithNoTypeMatchAndNullDataSerializesByteIdentical() throws Exception {
        Punishment punishment = new Punishment(
            "p-min", 9, null, null, new Date(1_600_000_000_000L), null,
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null);

        PunishmentView view = PunishmentMapper.toPunishmentView(punishment, List.of(), Map.of());
        Map<String, Object> legacy = legacyToPunishmentMap(punishment, PunishmentTypeIndex.byOrdinal(List.of()), Map.of());

        assertByteIdentical(legacy, view);
        assertEquals("Unknown", view.type());
    }

    @Test
    void detailShapeAppendsPlayerUuidThenPlayerNameByteIdentical() throws Exception {
        PunishmentType type = PunishmentType.builder().ordinal(3).name("Cheating").build();
        List<PunishmentType> types = List.of(type);
        Punishment punishment = fullyPopulatedPunishment();

        PunishmentView view = PunishmentMapper.toPunishmentView(punishment, types, ISSUERS)
            .withPlayer("uuid-1", "PlayerOne");

        Map<String, Object> legacy = legacyToPunishmentMap(punishment, PunishmentTypeIndex.byOrdinal(types), ISSUERS);
        legacy.put("playerUuid", "uuid-1");
        legacy.put("playerName", "PlayerOne");

        assertByteIdentical(legacy, view);
    }

    @Test
    void recentShapeIsTreeIdenticalToLegacyPlayerNameThenUuidOrdering() throws Exception {
        PunishmentType type = PunishmentType.builder().ordinal(3).name("Cheating").build();
        List<PunishmentType> types = List.of(type);
        Punishment punishment = fullyPopulatedPunishment();

        PunishmentView view = PunishmentMapper.toPunishmentView(punishment, types, ISSUERS)
            .withPlayer("uuid-1", "PlayerOne");

        Map<String, Object> legacy = legacyToPunishmentMap(punishment, PunishmentTypeIndex.byOrdinal(types), ISSUERS);
        legacy.put("playerName", "PlayerOne");
        legacy.put("playerUuid", "uuid-1");

        assertEquals(JSON.readTree(JSON.writeValueAsString(legacy)), JSON.readTree(JSON.writeValueAsString(view)));
    }

    private void assertByteIdentical(Map<String, Object> legacy, PunishmentView view) throws Exception {
        String legacyJson = JSON.writeValueAsString(legacy);
        String viewJson = JSON.writeValueAsString(view);
        assertEquals(legacyJson, viewJson);
        assertEquals(JSON.readTree(legacyJson), JSON.readTree(viewJson));
    }

    private Punishment fullyPopulatedPunishment() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reason", "x-ray");
        data.put("severity", "regular");

        PunishmentModification modification = new PunishmentModification(
            "mod-1", "DURATION_CHANGE", new Date(1_600_000_050_000L), null, "staff-1",
            "extended", 7_200_000L, null, Map.of("duration", "temporary"));
        PunishmentNote note = new PunishmentNote(
            "note-1", "manual note", new Date(1_600_000_060_000L), "Console", null);
        PunishmentEvidence evidence = new PunishmentEvidence(
            "screenshot", "https://example.com/e.png", "FILE", null, "staff-1",
            new Date(1_600_000_070_000L), "e.png", "image/png", 2048L);

        return new Punishment(
            "p-1", 3, null, "staff-1", new Date(1_600_000_000_000L), new Date(1_600_000_010_000L),
            new ArrayList<>(List.of(modification)), new ArrayList<>(List.of(note)),
            new ArrayList<>(List.of(evidence)), new ArrayList<>(List.of("ticket-1", "ticket-2")), data);
    }

    private static Map<String, Object> legacyToPunishmentMap(
        Punishment punishment,
        Map<Integer, PunishmentType> typesByOrdinal,
        Map<String, String> resolvedIssuers
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", punishment.getId());
        map.put("issuerName", PunishmentMapper.resolveIssuer(punishment.getIssuerId(), punishment.getIssuerName(), resolvedIssuers));
        map.put("issued", punishment.getIssued());
        map.put("started", punishment.getStarted());

        int ordinal = punishment.getTypeOrdinal();
        map.put("typeOrdinal", ordinal);

        PunishmentType matchedType = typesByOrdinal.get(ordinal);
        String actualTypeName = matchedType != null ? matchedType.getName() : null;
        map.put("type", actualTypeName != null ? actualTypeName : "Unknown");

        Map<String, Object> punishmentData = punishment.data().asMap();
        Map<String, Object> dataWithTypeName = punishmentData != null
            ? new LinkedHashMap<>(punishmentData) : new LinkedHashMap<>();
        if (actualTypeName != null) {
            dataWithTypeName.put("typeName", actualTypeName);
        }

        map.put("modifications", punishment.getModifications().stream().map(m -> {
            Map<String, Object> mod = new LinkedHashMap<>();
            mod.put("id", m.id());
            mod.put("type", m.type());
            mod.put("date", m.date());
            mod.put("issuerName", PunishmentMapper.resolveIssuer(m.issuerId(), m.issuerName(), resolvedIssuers));
            mod.put("effectiveDuration", m.effectiveDuration());
            mod.put("data", m.data());
            return mod;
        }).toList());

        map.put("notes", punishment.getNotes().stream().map(n -> {
            Map<String, Object> note = new LinkedHashMap<>();
            note.put("id", n.id());
            note.put("text", n.text());
            note.put("issuerName", PunishmentMapper.resolveIssuer(n.issuerId(), n.issuerName(), resolvedIssuers));
            note.put("date", n.date());
            return note;
        }).toList());

        map.put("evidence", punishment.getEvidence().stream().map(e -> {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("text", e.text());
            ev.put("url", e.url());
            ev.put("type", e.type());
            ev.put("uploadedBy", PunishmentMapper.resolveIssuer(e.uploadedById(), e.uploadedBy(), resolvedIssuers));
            ev.put("uploadedAt", e.uploadedAt());
            ev.put("fileName", e.fileName());
            ev.put("fileType", e.fileType());
            ev.put("fileSize", e.fileSize());
            return ev;
        }).toList());

        map.put("attachedTicketIds", punishment.getAttachedTicketIds());
        map.put("data", dataWithTypeName);

        return map;
    }
}
