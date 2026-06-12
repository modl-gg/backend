package gg.modl.backend.migration.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.migration.data.MigrationStatus;
import gg.modl.proto.modl.v1.MigrationOperationResponse;
import gg.modl.proto.modl.v1.MigrationStatusResponse;
import java.util.Map;
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

    public MigrationOperationResponse toOperationResponse(Map<String, Object> result) {
        MigrationOperationResponse.Builder builder = MigrationOperationResponse.newBuilder()
            .setSuccess(Boolean.TRUE.equals(result.get("success")));
        Object taskId = result.get("taskId");
        if (taskId != null) {
            builder.setTaskId(taskId.toString());
        }
        Object message = result.get("message");
        if (message != null) {
            builder.setMessage(message.toString());
        }
        Object error = result.get("error");
        if (error != null) {
            builder.setError(error.toString());
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
