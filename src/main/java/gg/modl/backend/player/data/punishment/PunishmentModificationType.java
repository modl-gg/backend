package gg.modl.backend.player.data.punishment;

import gg.modl.backend.ticket.data.AppealWorkflowStatus;

public enum PunishmentModificationType {
    MANUAL_PARDON,
    APPEAL_ACCEPT,
    APPEAL_REJECT,
    APPEAL_DURATION_CHANGE,
    SYSTEM_PARDON,
    MANUAL_DURATION_CHANGE,
    ROLLBACK,
    REMOVE,
    REVOKE;

    public boolean isPardon() {
        return this == MANUAL_PARDON || this == APPEAL_ACCEPT || this == SYSTEM_PARDON;
    }

    public static boolean isPardon(String type) {
        return MANUAL_PARDON.name().equals(type)
            || APPEAL_ACCEPT.name().equals(type)
            || SYSTEM_PARDON.name().equals(type);
    }

    public static boolean isDurationChange(String type) {
        return MANUAL_DURATION_CHANGE.name().equals(type)
            || APPEAL_DURATION_CHANGE.name().equals(type);
    }

    public AppealWorkflowStatus appealOutcome() {
        return switch (this) {
            case APPEAL_ACCEPT, APPEAL_DURATION_CHANGE -> AppealWorkflowStatus.APPROVED;
            case APPEAL_REJECT -> AppealWorkflowStatus.REJECTED;
            default -> null;
        };
    }

    public static PunishmentModificationType fromName(String type) {
        if (type == null) {
            return null;
        }
        try {
            return valueOf(type);
        } catch (IllegalArgumentException invalidType) {
            return null;
        }
    }
}
