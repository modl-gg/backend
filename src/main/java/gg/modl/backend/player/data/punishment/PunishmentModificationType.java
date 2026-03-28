package gg.modl.backend.player.data.punishment;

public enum PunishmentModificationType {
    MANUAL_PARDON,
    APPEAL_ACCEPT,
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
}
