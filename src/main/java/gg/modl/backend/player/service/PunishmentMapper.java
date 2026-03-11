package gg.modl.backend.player.service;

import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.settings.data.PunishmentType;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public final class PunishmentMapper {

    private PunishmentMapper() {}

    public static Map<String, Object> toPunishmentMap(Punishment punishment, List<PunishmentType> punishmentTypes) {
        return toPunishmentMap(punishment, punishmentTypes, Collections.emptyMap());
    }

    public static Map<String, Object> toPunishmentMap(Punishment punishment, List<PunishmentType> punishmentTypes, Map<String, String> resolvedIssuers) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", punishment.getId());
        map.put("issuerName", resolveIssuer(punishment.getIssuerId(), punishment.getIssuerName(), resolvedIssuers));
        map.put("issued", punishment.getIssued());
        map.put("started", punishment.getStarted());

        // Include the actual type ordinal for proper lookup
        int ordinal = punishment.getTypeOrdinal();
        map.put("typeOrdinal", ordinal);

        // Look up the actual punishment type name from the configured types
        String actualTypeName = punishmentTypes.stream()
            .filter(t -> t.getOrdinal() == ordinal)
            .findFirst()
            .map(PunishmentType::getName)
            .orElse(null);

        map.put("type", actualTypeName != null ? actualTypeName : "Unknown");

        // Include the actual type name in the data map for display
        Map<String, Object> dataWithTypeName = punishment.getData() != null ?
                                               new LinkedHashMap<>(punishment.getData()) : new LinkedHashMap<>();
        if (actualTypeName != null) {
            dataWithTypeName.put("typeName", actualTypeName);
        }

        // Convert modifications - include effectiveDuration for duration changes
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

        // Convert notes
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

        // Convert evidence
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
        return toSimplePunishment(punishment, types, statusCalculator, Collections.emptyMap());
    }

    public static Map<String, Object> toSimplePunishment(Punishment punishment, List<PunishmentType> types, PlayerStatusCalculator statusCalculator, Map<String, String> resolvedIssuers) {
        Map<String, Object> data = punishment.getData();
        Date expires = statusCalculator.getEffectiveExpiry(punishment);

        PunishmentType punishmentType = types.stream()
            .filter(t -> t.getOrdinal() == punishment.getTypeOrdinal())
            .findFirst()
            .orElse(null);

        String typeName = punishmentType != null ? punishmentType.getName() : "Unknown";
        String playerDescription = punishmentType != null ? punishmentType.getPlayerDescription() : null;

        // Determine effective category: BAN, MUTE, or OTHER
        String effectiveCategory = statusCalculator.getEffectiveCategory(punishmentType, data);
        String category = effectiveCategory != null ? effectiveCategory : "OTHER";

        // For manual punishments (ordinals 0-5: kick, mute, ban, security ban, linked ban, blacklist),
        // the reason is stored as the first non-auto-generated note
        String reason = null;
        if (punishment.getTypeOrdinal() <= 5 && punishment.getNotes() != null && !punishment.getNotes().isEmpty()) {
            for (var note : punishment.getNotes()) {
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

        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;

        if (months > 0) {
            return months + (months == 1 ? " month" : " months");
        } else if (weeks > 0) {
            return weeks + (weeks == 1 ? " week" : " weeks");
        } else if (days > 0) {
            return days + (days == 1 ? " day" : " days");
        } else if (hours > 0) {
            return hours + (hours == 1 ? " hour" : " hours");
        } else if (minutes > 0) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        } else {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
    }
}
