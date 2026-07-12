package gg.modl.backend.player.service;

import gg.modl.backend.player.data.punishment.EnforcementCategory;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentDataView;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentModificationType;
import gg.modl.backend.player.data.punishment.PunishmentStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.DurationDetail;
import gg.modl.backend.settings.data.OffenderThresholdSettings;
import gg.modl.backend.settings.data.PunishmentDurationResolver;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.OffenderThresholdSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeIndex;
import gg.modl.backend.settings.service.PunishmentTypeService;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlayerStatusCalculator {
    private final PunishmentTypeService punishmentTypeService;
    private final OffenderThresholdSettingsService offenderThresholdSettingsService;

    public PlayerStatus calculateStatus(Server server, List<Punishment> punishments) {
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        Map<Integer, PunishmentType> typesByOrdinal = PunishmentTypeIndex.byOrdinal(types);
        OffenderThresholdSettings thresholdSettings = offenderThresholdSettingsService.getThresholdSettings(server);
        long socialExpiryMs = thresholdSettings.getSocial().getPointExpiryMs();
        long gameplayExpiryMs = thresholdSettings.getGameplay().getPointExpiryMs();
        long now = System.currentTimeMillis();

        int socialPoints = 0;
        int gameplayPoints = 0;

        for (Punishment punishment : punishments) {
            if (!isPunishmentEligible(punishment)) {
                continue;
            }

            int typeOrdinal = punishment.getTypeOrdinal();
            String severity = punishment.data().severity();

            PunishmentType type = typesByOrdinal.get(typeOrdinal);
            if (type == null) {
                continue;
            }

            Date effectiveExpiry = getEffectiveExpiry(punishment);
            if (effectiveExpiry != null) {
                long expiryMs = type.isSocial() ? socialExpiryMs : gameplayExpiryMs;
                if (effectiveExpiry.getTime() + expiryMs < now) {
                    continue;
                }
            }

            int points = type.getPointsForSeverity(severity != null ? severity : "regular");

            if (type.isSocial()) {
                socialPoints += points;
            } else if (type.isGameplay()) {
                gameplayPoints += points;
            }
        }

        String socialStatus = thresholdSettings.getSocialOffenderLevel(socialPoints);
        String gameplayStatus = thresholdSettings.getGameplayOffenderLevel(gameplayPoints);

        return new PlayerStatus(socialStatus, gameplayStatus, socialPoints, gameplayPoints);
    }

    public boolean isPunishmentActive(Punishment punishment) {
        if (!isPunishmentEligible(punishment)) {
            return false;
        }

        Date effectiveExpiry = getEffectiveExpiry(punishment);
        return effectiveExpiry == null || !effectiveExpiry.before(new Date());
    }

    private boolean isPunishmentEligible(Punishment punishment) {
        if (punishment.getTypeOrdinal() == 0) {
            return false;
        }

        PunishmentDataView data = punishment.data();
        if (data.asMap() == null) {
            return false;
        }

        if (data.isUnstarted()) {
            return false;
        }

        return !punishment.isPardoned();
    }


    public Date getEffectiveExpiry(Punishment punishment) {
        PunishmentDataView data = punishment.data();
        if (data.asMap() == null) {
            return null;
        }

        PunishmentModification latestDurationChange = null;
        for (PunishmentModification mod : punishment.getModifications()) {
            if (PunishmentModificationType.isDurationChange(mod.type())) {
                latestDurationChange = mod;
            }
        }

        if (latestDurationChange != null) {
            Long eff = latestDurationChange.effectiveDuration();
            if (eff == null || eff <= 0) {
                return null;
            }
            return new Date(latestDurationChange.date().getTime() + eff);
        }

        Long duration = data.duration();
        if (duration == null || duration <= 0) {
            return null;
        }

        Date baseDate = punishment.getStarted() != null ? punishment.getStarted() : new Date();
        return new Date(baseDate.getTime() + duration);
    }

    public String getEffectiveCategory(Punishment punishment, List<PunishmentType> types) {
        return getEffectiveCategory(punishment, PunishmentTypeIndex.byOrdinal(types));
    }

    public String getEffectiveCategory(Punishment punishment, Map<Integer, PunishmentType> typesByOrdinal) {
        PunishmentType pt = typesByOrdinal.get(punishment.getTypeOrdinal());
        return getEffectiveCategory(pt, punishment.data());
    }

    public String getEffectiveCategory(PunishmentType pt, PunishmentDataView data) {
        if (pt == null) {
            return null;
        }
        String storedCategory = data.enforcementCategory();
        if (storedCategory != null) {
            return storedCategory;
        }
        if (pt.isKick()) {
            return null;
        }
        if (pt.isBan()) {
            return EnforcementCategory.BAN.name();
        }
        if (pt.isMute()) {
            return EnforcementCategory.MUTE.name();
        }

        if (data.asMap() != null) {
            String severity = data.severity() != null ? data.severity() : "regular";
            String offenseLevel;
            String rawOffenseLevel = data.offenseLevel();
            if (rawOffenseLevel != null) {
                offenseLevel = rawOffenseLevel;
            } else {
                String status = data.status();
                if (status == null
                    || PunishmentStatus.UNSTARTED.equals(status)
                    || PunishmentStatus.PARDONED.equals(status)) {
                    offenseLevel = "first";
                } else {
                    offenseLevel = switch (status.toLowerCase(Locale.ROOT)) {
                        case "low" -> "first";
                        case "medium" -> "medium";
                        case "habitual" -> "habitual";
                        default -> "first";
                    };
                }
            }
            DurationDetail detail = PunishmentDurationResolver.resolveDetail(pt, severity, offenseLevel);
            if (detail != null) {
                if (detail.isBan()) {
                    return EnforcementCategory.BAN.name();
                }
                if (detail.isMute()) {
                    return EnforcementCategory.MUTE.name();
                }
            }
        }
        return null;
    }

    public boolean isPunishmentNaturallyExpired(Punishment punishment) {
        if (punishment.getStarted() == null) {
            return false;
        }

        if (punishment.getTypeOrdinal() == 0) {
            return false;
        }

        if (punishment.isPardoned()) {
            return false;
        }

        Date effectiveExpiry = getEffectiveExpiry(punishment);
        if (effectiveExpiry == null) {
            return false;
        }

        return effectiveExpiry.before(new Date());
    }

    public record PlayerStatus(
        String social,
        String gameplay,
        int socialPoints,
        int gameplayPoints
    ) {
    }
}
