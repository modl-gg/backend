package gg.modl.backend.player.service;

import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.DurationDetail;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlayerStatusCalculator {
    private final PunishmentTypeService punishmentTypeService;

    public PlayerStatus calculateStatus(Server server, List<Punishment> punishments) {
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);

        int socialPoints = 0;
        int gameplayPoints = 0;

        for (Punishment punishment : punishments) {
            if (!isPunishmentActive(punishment)) {
                continue;
            }

            int typeOrdinal = punishment.getType_ordinal();
            Map<String, Object> data = punishment.getData();
            String severity = data != null ? (String) data.get("severity") : null;

            Optional<PunishmentType> typeOpt = findTypeByOrdinal(types, typeOrdinal);
            if (typeOpt.isEmpty()) {
                continue;
            }

            PunishmentType type = typeOpt.get();
            int points = type.getPointsForSeverity(severity != null ? severity : "regular");

            if (type.isSocial()) {
                socialPoints += points;
            } else if (type.isGameplay()) {
                gameplayPoints += points;
            }
        }

        String socialStatus = getStatusFromPoints(socialPoints);
        String gameplayStatus = getStatusFromPoints(gameplayPoints);

        return new PlayerStatus(socialStatus, gameplayStatus, socialPoints, gameplayPoints);
    }

    public boolean isPunishmentActive(Punishment punishment) {
        String pId = punishment.getId();

        // Kicks (ordinal 0) are instant and never considered "active"
        if (punishment.getType_ordinal() == 0) {
            return false;
        }

        Map<String, Object> data = punishment.getData();
        if (data == null) {
            return false;
        }

        // Queued punishments (status = "Unstarted") are not yet active
        Object statusObj = data.get("status");
        String status = statusObj instanceof String ? (String) statusObj : null;
        if ("Unstarted".equals(status)) {
            return false;
        }

        for (PunishmentModification mod : punishment.getModifications()) {
            String type = mod.type();
            if ("MANUAL_PARDON".equals(type) || "APPEAL_ACCEPT".equals(type) || "SYSTEM_PARDON".equals(type)) {
                return false;
            }
        }

        // Check legacy "expires" field first
        Object expiresObj = data.get("expires");
        if (expiresObj != null) {
            Date expires;
            if (expiresObj instanceof Date) {
                expires = (Date) expiresObj;
            } else if (expiresObj instanceof Long) {
                expires = new Date((Long) expiresObj);
            } else {
                return true;
            }

            return !expires.before(new Date());
        }

        // Check duration-based expiry
        Date effectiveExpiry = getEffectiveExpiry(punishment);

        return effectiveExpiry == null || !effectiveExpiry.before(new Date());
    }

    public Date getEffectiveExpiry(Punishment punishment) {
        Map<String, Object> data = punishment.getData();
        if (data == null) {
            return null;
        }

        Long duration = null;
        Date durationBase = null;
        for (PunishmentModification mod : punishment.getModifications()) {
            if (mod.effectiveDuration() != null) {
                duration = mod.effectiveDuration();
                durationBase = mod.date();
            }
        }

        if (duration == null) {
            Object durationObj = data.get("duration");
            if (durationObj instanceof Long) {
                duration = (Long) durationObj;
            } else if (durationObj instanceof Integer) {
                duration = ((Integer) durationObj).longValue();
            } else if (durationObj instanceof Double) {
                duration = ((Double) durationObj).longValue();
            } else if (durationObj instanceof Number) {
                // Catch-all for any other numeric type
                duration = ((Number) durationObj).longValue();
            }
        }

        // null, 0, or negative (-1L) indicates permanent (no expiry)
        if (duration == null || duration <= 0) {
            return null;
        }

        // If duration came from a modification, count from the modification date
        if (durationBase != null) {
            return new Date(durationBase.getTime() + duration);
        }

        // Otherwise count from started date (original unmodified punishment)
        Date started = punishment.getStarted();
        if (started == null) {
            return null; // Not started yet, no expiry
        }
        return new Date(started.getTime() + duration);
    }

    /**
     * Determine the effective enforcement category for a punishment.
     * Core types use isBan()/isMute()/isKick().
     * Social/gameplay types use the DurationDetail for the stored severity and offense level.
     * @return "BAN", "MUTE", or null (for kicks and unknown types)
     */
    public String getEffectiveCategory(Punishment punishment, List<PunishmentType> types) {
        PunishmentType pt = types.stream()
                .filter(t -> t.getOrdinal() == punishment.getType_ordinal())
                .findFirst()
                .orElse(null);
        return getEffectiveCategory(pt, punishment.getData());
    }

    /**
     * Determine the effective enforcement category for a punishment type with given data.
     * @return "BAN", "MUTE", or null
     */
    public String getEffectiveCategory(PunishmentType pt, Map<String, Object> data) {
        if (pt == null) return null;
        if (pt.isKick()) return null;
        if (pt.isBan()) return "BAN";
        if (pt.isMute()) return "MUTE";

        // Social/gameplay types: determine from DurationDetail
        if (data != null) {
            String severity = data.get("severity") instanceof String s ? s : "regular";
            String offenseLevel = data.get("offenseLevel") instanceof String s ? s : "normal";
            DurationDetail detail = pt.getDurationDetail(severity, offenseLevel);
            if (detail != null) {
                if (detail.isBan()) return "BAN";
                if (detail.isMute()) return "MUTE";
            }
        }
        return null;
    }

    private Optional<PunishmentType> findTypeByOrdinal(List<PunishmentType> types, int ordinal) {
        return types.stream()
                .filter(t -> t.getOrdinal() == ordinal)
                .findFirst();
    }

    private String getStatusFromPoints(int points) {
        if (points == 0) {
            return "Good";
        } else if (points <= 2) {
            return "Warning";
        } else if (points <= 5) {
            return "Restricted";
        } else {
            return "Banned";
        }
    }

    public record PlayerStatus(
            String social,
            String gameplay,
            int socialPoints,
            int gameplayPoints
    ) {
    }
}
