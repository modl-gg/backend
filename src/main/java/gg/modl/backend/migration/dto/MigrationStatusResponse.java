package gg.modl.backend.migration.dto;

import gg.modl.backend.migration.data.MigrationStatus;

import java.util.Date;

public record MigrationStatusResponse(
        CurrentMigration currentMigration,
        CooldownInfo cooldown
) {
    public record CurrentMigration(
            String taskId,
            String type,
            String status,
            MigrationStatus.MigrationProgress progress,
            Date startedAt,
            Date completedAt,
            String error
    ) {}

    public record CooldownInfo(
            boolean onCooldown,
            Long remainingTime
    ) {}
}
