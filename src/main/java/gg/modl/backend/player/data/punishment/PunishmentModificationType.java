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

    public boolean isDurationChange() {
        return this == MANUAL_DURATION_CHANGE || this == APPEAL_DURATION_CHANGE;
    }

    public static boolean isPardon(String type) {
        PunishmentModificationType value = fromName(type);
        return value != null && value.isPardon();
    }

    public static boolean isDurationChange(String type) {
        PunishmentModificationType value = fromName(type);
        return value != null && value.isDurationChange();
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
