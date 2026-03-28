package gg.modl.backend.player.service;

import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeIndex;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public final class PunishmentMapper {

    private PunishmentMapper() {}

    public static Map<String, Object> toPunishmentMap(Punishment punishment, List<PunishmentType> punishmentTypes) {
        return toPunishmentMap(punishment, PunishmentTypeIndex.byOrdinal(punishmentTypes), Collections.emptyMap());
    }

    public static Map<String, Object> toPunishmentMap(Punishment punishment, List<PunishmentType> punishmentTypes, Map<String, String> resolvedIssuers) {
        return toPunishmentMap(punishment, PunishmentTypeIndex.byOrdinal(punishmentTypes), resolvedIssuers);
    }

    public static Map<String, Object> toPunishmentMap(Punishment punishment, Map<Integer, PunishmentType> typesByOrdinal, Map<String, String> resolvedIssuers) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", punishment.getId());
        map.put("issuerName", resolveIssuer(punishment.getIssuerId(), punishment.getIssuerName(), resolvedIssuers));
        map.put("issued", punishment.getIssued());
        map.put("started", punishment.getStarted());

        int ordinal = punishment.getTypeOrdinal();
        map.put("typeOrdinal", ordinal);

        PunishmentType matchedType = typesByOrdinal.get(ordinal);
        String actualTypeName = matchedType != null ? matchedType.getName() : null;

        map.put("type", actualTypeName != null ? actualTypeName : "Unknown");

        Map<String, Object> dataWithTypeName = punishment.getData() != null ?
                                               new LinkedHashMap<>(punishment.getData()) : new LinkedHashMap<>();
        if (actualTypeName != null) {
            dataWithTypeName.put("typeName", actualTypeName);
        }

        List<Map<String, Object>> modifications = punishment.getModifications()
            .stream()
            .map(m -> {
                Map<String, Object> mod = new LinkedHashMap<>();
                mod.put("id", m.id());
                mod.put("type", m.type());
                mod.put("date", m.date());
                mod.put("issuerName", resolveIssuer(m.issuerId(), m.issuerName(), resolvedIssuers));
                mod.put("effectiveDuration", m.effectiveDuration());
                mod.put("data", m.data());
                return mod;
            }).toList();
        map.put("modifications", modifications);

        List<Map<String, Object>> notes = punishment.getNotes()
            .stream()
            .map(n -> {
                Map<String, Object> note = new LinkedHashMap<>();
                note.put("id", n.id());
                note.put("text", n.text());
                note.put("issuerName", resolveIssuer(n.issuerId(), n.issuerName(), resolvedIssuers));
                note.put("date", n.date());
                return note;
            }).toList();
        map.put("notes", notes);

        List<Map<String, Object>> evidence = punishment.getEvidence()
            .stream()
            .map(e -> {
                Map<String, Object> ev = new LinkedHashMap<>();
                ev.put("text", e.text());
                ev.put("url", e.url());
                ev.put("type", e.type());
                ev.put("uploadedBy", resolveIssuer(e.uploadedById(), e.uploadedBy(), resolvedIssuers));
                ev.put("uploadedAt", e.uploadedAt());
                ev.put("fileName", e.fileName());
                ev.put("fileType", e.fileType());
                ev.put("fileSize", e.fileSize());
                return ev;
            }).toList();
        map.put("evidence", evidence);

        map.put("attachedTicketIds", punishment.getAttachedTicketIds());
        map.put("data", dataWithTypeName);

        return map;
    }

    static String resolveIssuer(@Nullable String issuerId, @Nullable String issuerName, Map<String, String> resolvedIssuers) {
        if (issuerId != null && resolvedIssuers.containsKey(issuerId)) {
            return resolvedIssuers.get(issuerId);
        }
        if (issuerName != null) {
            return issuerName;
        }
        return issuerId != null ? "Unknown Staff" : "Console";
    }

    public static Map<String, Object> toSimplePunishment(Punishment punishment, List<PunishmentType> types, PlayerStatusCalculator statusCalculator) {
        return toSimplePunishment(punishment, PunishmentTypeIndex.byOrdinal(types), statusCalculator, Collections.emptyMap());
    }

    public static Map<String, Object> toSimplePunishment(Punishment punishment, List<PunishmentType> types, PlayerStatusCalculator statusCalculator, Map<String, String> resolvedIssuers) {
        return toSimplePunishment(punishment, PunishmentTypeIndex.byOrdinal(types), statusCalculator, resolvedIssuers);
    }

    public static Map<String, Object> toSimplePunishment(Punishment punishment, Map<Integer, PunishmentType> typesByOrdinal, PlayerStatusCalculator statusCalculator, Map<String, String> resolvedIssuers) {
        Map<String, Object> data = punishment.getData();
        Date expires = statusCalculator.getEffectiveExpiry(punishment);

        PunishmentType punishmentType = typesByOrdinal.get(punishment.getTypeOrdinal());

        String typeName = punishmentType != null ? punishmentType.getName() : "Unknown";
        String playerDescription = punishmentType != null ? punishmentType.getPlayerDescription() : null;

        String effectiveCategory = statusCalculator.getEffectiveCategory(punishmentType, data);
        String category = effectiveCategory != null ? effectiveCategory : "OTHER";

        String reason = null;
        if (punishment.getTypeOrdinal() <= 5 && punishment.getNotes() != null && !punishment.getNotes().isEmpty()) {
            for (PunishmentNote note : punishment.getNotes()) {
                String noteText = note.text();
                if (noteText != null && !isAutoGeneratedNote(noteText)) {
                    reason = noteText;
                    break;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", punishment.getId());
        result.put("type", typeName);
        result.put("category", category);
        result.put("ordinal", punishment.getTypeOrdinal());
        result.put("started", punishment.getStarted() != null);
        result.put("expiration", expires != null ? expires.getTime() : null);
        result.put("description", reason != null ? reason : "No reason specified");
        result.put("issuerName", resolveIssuer(punishment.getIssuerId(), punishment.getIssuerName(), resolvedIssuers));
        result.put("issuedAt", punishment.getIssued().getTime());
        result.put("playerDescription", playerDescription);
        result.put("modifications", punishment.getModifications()
            .stream().map(m -> {
                Map<String, Object> modMap = new LinkedHashMap<>();
                modMap.put("type", m.type());
                modMap.put("timestamp", m.date() != null ? m.date().getTime() : null);
                modMap.put("effectiveDuration", m.effectiveDuration() != null ? m.effectiveDuration() : 0L);
                modMap.put("issuerName", resolveIssuer(m.issuerId(), m.issuerName(), resolvedIssuers));
                return modMap;
            }).toList());

        return result;
    }

    public static boolean isAutoGeneratedNote(String noteText) {
        if (noteText == null) {
            return true;
        }
        String lower = noteText.toLowerCase();
        return lower.equals("issued punishment") ||
               lower.startsWith("issued ") ||
               lower.equals("pardoned punishment") ||
               lower.equals("added evidence") ||
               lower.startsWith("changed duration to ") ||
               lower.startsWith("enabled ") ||
               lower.startsWith("disabled ");
    }

    public static String formatDuration(long durationMs, boolean isPermanent) {
        if (isPermanent || durationMs < 0) {
            return "Permanent";
        }
        if (durationMs == 0) {
            return "Instant";
        }

        long totalSeconds = durationMs / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d");
        if (hours > 0) { if (!sb.isEmpty()) sb.append(" "); sb.append(hours).append("h"); }
        if (minutes > 0) { if (!sb.isEmpty()) sb.append(" "); sb.append(minutes).append("m"); }
        if (seconds > 0 && days == 0) { if (!sb.isEmpty()) sb.append(" "); sb.append(seconds).append("s"); }

        return sb.isEmpty() ? "0s" : sb.toString();
    }
}
