package gg.modl.backend.staff.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalString;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalStrings;

import gg.modl.backend.staff.dto.response.MinecraftStaffPermissionsResponse;
import gg.modl.backend.staff.dto.response.MinecraftStaffSummaryResponse;
import gg.modl.proto.modl.v1.StaffListResponse;
import gg.modl.proto.modl.v1.StaffPermissionsListResponse;
import java.util.List;

final class MinecraftStaffProtoMapper {
    private MinecraftStaffProtoMapper() {
    }

    static StaffListResponse toStaffListResponse(
        List<MinecraftStaffSummaryResponse> staffList
    ) {
        StaffListResponse.Builder response = StaffListResponse.newBuilder()
            .setStatus(200);

        if (staffList != null) {
            staffList.stream()
                .forEach(staff -> addMinecraftStaffSummaryResponse(response, staff));
        }

        return response.build();
    }

    static StaffPermissionsListResponse toStaffPermissionsListResponse(
        List<MinecraftStaffPermissionsResponse> staffList
    ) {
        StaffPermissionsListResponse.StaffPermissionsData.Builder data =
            StaffPermissionsListResponse.StaffPermissionsData.newBuilder();

        if (staffList != null) {
            staffList.stream()
                .forEach(staff -> addMinecraftStaffPermissionsResponse(data, staff));
        }

        return StaffPermissionsListResponse.newBuilder()
            .setStatus(200)
            .setData(data)
            .build();
    }

    private static void addMinecraftStaffSummaryResponse(
        StaffListResponse.Builder response,
        MinecraftStaffSummaryResponse staff
    ) {
        var staffBuilder = response.addStaffBuilder()
            .setLastSeen(longValue(staff.lastSeen()))
            .setTotalPlaytimeMs(staff.totalPlaytimeMs())
            .setPunishmentsIssuedCount(staff.punishmentsIssuedCount())
            .setCreatedAt(longValue(staff.createdAt()))
            .setUpdatedAt(longValue(staff.updatedAt()));

        setOptionalString(staffBuilder::setId, staff.id());
        setOptionalString(staffBuilder::setUsername, staff.username());
        setOptionalString(staffBuilder::setEmail, staff.email());
        setOptionalString(staffBuilder::setRole, staff.role());
        setOptionalString(staffBuilder::setMinecraftUuid, staff.minecraftUuid());
        setOptionalString(staffBuilder::setMinecraftUsername, staff.minecraftUsername());
        setOptionalString(staffBuilder::setLastServer, staff.lastServer());
        setOptionalStrings(staffBuilder::addAllPermissions, staff.permissions());
    }

    private static void addMinecraftStaffPermissionsResponse(
        StaffPermissionsListResponse.StaffPermissionsData.Builder data,
        MinecraftStaffPermissionsResponse staff
    ) {
        var staffBuilder = data.addStaffBuilder();

        setOptionalString(staffBuilder::setMinecraftUuid, staff.minecraftUuid());
        setOptionalString(staffBuilder::setMinecraftUsername, staff.minecraftUsername());
        setOptionalString(staffBuilder::setStaffUsername, staff.staffUsername());
        setOptionalString(staffBuilder::setStaffId, staff.staffId());
        setOptionalString(staffBuilder::setStaffRole, staff.staffRole());
        setOptionalStrings(staffBuilder::addAllPermissions, staff.permissions());
        setOptionalString(staffBuilder::setEmail, staff.email());
    }
}
