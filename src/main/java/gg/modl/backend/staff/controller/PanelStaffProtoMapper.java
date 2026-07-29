package gg.modl.backend.staff.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.nullToEmpty;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalEpochMillis;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalString;

import gg.modl.backend.staff.dto.request.AssignMinecraftPlayerRequest;
import gg.modl.backend.staff.dto.request.CreateStaffRequest;
import gg.modl.backend.staff.dto.request.InviteStaffRequest;
import gg.modl.backend.staff.dto.request.UpdateStaffRequest;
import gg.modl.backend.staff.dto.response.AvailablePlayerResponse;
import gg.modl.backend.staff.dto.response.InviteResultResponse;
import gg.modl.backend.staff.dto.response.StaffResponse;
import gg.modl.proto.modl.v1.AvailablePlayersResponse;
import gg.modl.proto.modl.v1.CheckUsernameResponse;
import gg.modl.proto.modl.v1.InviteResultResponse.FailedInvite;
import gg.modl.proto.modl.v1.PanelStaffListResponse;
import gg.modl.proto.modl.v1.StaffMutationResponse;
import java.util.List;

final class PanelStaffProtoMapper {
    private PanelStaffProtoMapper() {
    }

    static CreateStaffRequest toCreateStaffRequest(gg.modl.proto.modl.v1.CreateStaffRequest request) {
        return new CreateStaffRequest(
            request.getEmail(),
            request.getUsername(),
            request.hasRole() ? request.getRole() : null
        );
    }

    static UpdateStaffRequest toUpdateStaffRequest(gg.modl.proto.modl.v1.UpdateStaffRequest request) {
        return new UpdateStaffRequest(
            request.hasEmail() ? request.getEmail() : null
        );
    }

    static InviteStaffRequest toInviteStaffRequest(gg.modl.proto.modl.v1.InviteStaffRequest request) {
        return new InviteStaffRequest(
            request.hasEmail() ? request.getEmail() : null,
            request.getEmailsList().isEmpty() ? null : List.copyOf(request.getEmailsList()),
            request.getRole()
        );
    }

    static AssignMinecraftPlayerRequest toAssignMinecraftPlayerRequest(gg.modl.proto.modl.v1.AssignMinecraftPlayerRequest request) {
        return new AssignMinecraftPlayerRequest(
            request.hasMinecraftUuid() ? request.getMinecraftUuid() : null,
            request.hasMinecraftUsername() ? request.getMinecraftUsername() : null
        );
    }

    static PanelStaffListResponse toStaffListResponse(List<StaffResponse> staff) {
        PanelStaffListResponse.Builder builder = PanelStaffListResponse.newBuilder();
        staff.stream().map(PanelStaffProtoMapper::toStaffResponse).forEach(builder::addStaff);
        return builder.build();
    }

    static CheckUsernameResponse toCheckUsernameResponse(boolean exists) {
        return CheckUsernameResponse.newBuilder().setExists(exists).build();
    }

    static gg.modl.proto.modl.v1.StaffResponse toStaffResponse(StaffResponse staff) {
        gg.modl.proto.modl.v1.StaffResponse.Builder builder = gg.modl.proto.modl.v1.StaffResponse.newBuilder();
        setOptionalString(builder::setId, staff.id());
        setOptionalString(builder::setEmail, staff.email());
        setOptionalString(builder::setUsername, staff.username());
        setOptionalString(builder::setRole, staff.role());
        setOptionalString(builder::setStatus, staff.status());
        setOptionalString(builder::setAssignedMinecraftUuid, staff.assignedMinecraftUuid());
        setOptionalString(builder::setAssignedMinecraftUsername, staff.assignedMinecraftUsername());
        setOptionalEpochMillis(builder::setCreatedAt, staff.createdAt());
        return builder.build();
    }

    static StaffMutationResponse toStaffMutationResponse(String message, StaffResponse staff) {
        StaffMutationResponse.Builder builder = StaffMutationResponse.newBuilder().setMessage(message);
        if (staff != null) {
            builder.setStaffMember(toStaffResponse(staff));
        }
        return builder.build();
    }

    static gg.modl.proto.modl.v1.InviteResultResponse toInviteResultResponse(InviteResultResponse result) {
        gg.modl.proto.modl.v1.InviteResultResponse.Builder builder =
            gg.modl.proto.modl.v1.InviteResultResponse.newBuilder();
        setOptionalString(builder::setMessage, result.message());
        if (result.success() != null) {
            builder.addAllSuccess(result.success());
        }
        if (result.failed() != null) {
            result.failed().stream()
                .map(failed -> FailedInvite.newBuilder()
                    .setEmail(nullToEmpty(failed.email()))
                    .setReason(nullToEmpty(failed.reason()))
                    .build())
                .forEach(builder::addFailed);
        }
        return builder.build();
    }

    static AvailablePlayersResponse toAvailablePlayersResponse(List<AvailablePlayerResponse> players) {
        AvailablePlayersResponse.Builder builder = AvailablePlayersResponse.newBuilder();
        players.stream()
            .map(player -> gg.modl.proto.modl.v1.AvailablePlayerResponse.newBuilder()
                .setUuid(nullToEmpty(player.uuid()))
                .setUsername(nullToEmpty(player.username()))
                .build())
            .forEach(builder::addPlayers);
        return builder.build();
    }
}
