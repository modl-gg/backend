package gg.modl.backend.settings.service;

import gg.modl.backend.exception.ConflictException;
import lombok.Getter;

@Getter
public class SettingsConflictException extends ConflictException {
    private final long currentVersion;

    public SettingsConflictException(String message, long currentVersion) {
        super(message);
        this.currentVersion = currentVersion;
    }
}
