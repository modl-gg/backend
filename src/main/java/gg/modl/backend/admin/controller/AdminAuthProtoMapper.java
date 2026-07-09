package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.data.AdminUser;
import gg.modl.proto.modl.v1.AdminAuthResponse;
import gg.modl.proto.modl.v1.AdminLoginResponse;
import gg.modl.proto.modl.v1.AdminSessionData;
import gg.modl.proto.modl.v1.AdminSessionResponse;
import gg.modl.proto.modl.v1.AdminUserData;

import java.util.Date;
import java.util.List;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

final class AdminAuthProtoMapper {

    private AdminAuthProtoMapper() {
    }

    static AdminAuthResponse toAuthResponse(boolean success, String message) {
        return AdminAuthResponse.newBuilder()
            .setSuccess(success)
            .setMessage(stringValue(message))
            .build();
    }

    static AdminLoginResponse toLoginResponse(boolean success, String message, AdminUser admin) {
        AdminLoginResponse.Builder builder = AdminLoginResponse.newBuilder()
            .setSuccess(success)
            .setMessage(stringValue(message));
        if (admin != null) {
            builder.setData(toUserData(admin));
        }
        return builder.build();
    }

    static AdminSessionResponse toSessionResponse(boolean success, AdminUser admin) {
        AdminSessionResponse.Builder builder = AdminSessionResponse.newBuilder()
            .setSuccess(success);
        if (admin != null) {
            builder.setData(toSessionData(admin));
        }
        return builder.build();
    }

    private static AdminUserData toUserData(AdminUser admin) {
        AdminUserData.Builder builder = AdminUserData.newBuilder()
            .setEmail(stringValue(admin.getEmail()));
        Date lastActivity = admin.getLastActivityAt();
        if (lastActivity != null) {
            builder.setLastActivityAt(toTimestamp(lastActivity));
        }
        return builder.build();
    }

    private static AdminSessionData toSessionData(AdminUser admin) {
        AdminSessionData.Builder builder = AdminSessionData.newBuilder()
            .setEmail(stringValue(admin.getEmail()))
            .setIsAuthenticated(true);
        Date lastActivity = admin.getLastActivityAt();
        if (lastActivity != null) {
            builder.setLastActivityAt(toTimestamp(lastActivity));
        }
        List<String> loggedInIps = admin.getLoggedInIps();
        if (loggedInIps != null) {
            builder.addAllLoggedInIps(loggedInIps);
        }
        return builder.build();
    }
}
