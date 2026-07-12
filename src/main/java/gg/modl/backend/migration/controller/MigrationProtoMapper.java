package gg.modl.backend.migration.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.nullToEmpty;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.migration.data.MigrationStatus;
import gg.modl.backend.migration.service.MigrationService.MigrationOperationResult;
import gg.modl.proto.modl.v1.MigrationOperationResponse;
import gg.modl.proto.modl.v1.MigrationStatusResponse;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class MigrationProtoMapper {

    public MigrationStatusResponse toStatusResponse(@Nullable MigrationStatus status,
                                                    boolean onCooldown,
                                                    @Nullable Long remainingTime) {
        MigrationStatusResponse.Builder builder = MigrationStatusResponse.newBuilder();

        if (status != null) {
            MigrationStatusResponse.CurrentMigration.Builder current =
                MigrationStatusResponse.CurrentMigration.newBuilder()
                    .setTaskId(nullToEmpty(status.getTaskId()))
                    .setType(nullToEmpty(status.getType()))
                    .setStatus(nullToEmpty(status.getStatus()))
                    .setError(nullToEmpty(status.getError()));
            if (status.getStartedAt() != null) {
                current.setStartedAt(toTimestamp(status.getStartedAt()));
            }
            if (status.getCompletedAt() != null) {
                current.setCompletedAt(toTimestamp(status.getCompletedAt()));
            }
            if (status.getProgress() != null) {
                current.setProgress(toProgress(status.getProgress()));
            }
            builder.setCurrentMigration(current);
        }

        MigrationStatusResponse.CooldownInfo.Builder cooldown =
            MigrationStatusResponse.CooldownInfo.newBuilder()
                .setOnCooldown(onCooldown);
        if (remainingTime != null) {
            cooldown.setRemainingTime(remainingTime);
        }
        builder.setCooldown(cooldown);

        return builder.build();
    }

    public MigrationOperationResponse toOperationResponse(MigrationOperationResult result) {
        MigrationOperationResponse.Builder builder = MigrationOperationResponse.newBuilder()
            .setSuccess(result.success());
        if (result.taskId() != null) {
            builder.setTaskId(result.taskId());
        }
        if (result.message() != null) {
            builder.setMessage(result.message());
        }
        if (result.error() != null) {
            builder.setError(result.error());
        }
        return builder.build();
    }

    private MigrationStatusResponse.MigrationProgress toProgress(MigrationStatus.MigrationProgress progress) {
        MigrationStatusResponse.MigrationProgress.Builder builder =
            MigrationStatusResponse.MigrationProgress.newBuilder()
                .setMessage(nullToEmpty(progress.getMessage()));
        if (progress.getRecordsProcessed() != null) {
            builder.setRecordsProcessed(progress.getRecordsProcessed());
        }
        if (progress.getRecordsSkipped() != null) {
            builder.setRecordsSkipped(progress.getRecordsSkipped());
        }
        if (progress.getTotalRecords() != null) {
            builder.setTotalRecords(progress.getTotalRecords());
        }
        return builder.build();
    }
}
