package gg.modl.backend.staff.dto.request;

public record AssignMinecraftPlayerRequest(
        String minecraftUuid,
        String minecraftUsername
) {
}
