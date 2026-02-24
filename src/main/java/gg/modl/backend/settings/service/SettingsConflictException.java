package gg.modl.backend.settings.service;

public class SettingsConflictException extends RuntimeException {
    private final long currentVersion;

    public SettingsConflictException(String message, long currentVersion) {
        super(message);
        this.currentVersion = currentVersion;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }
}
