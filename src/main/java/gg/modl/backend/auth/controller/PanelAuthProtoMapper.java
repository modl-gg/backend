package gg.modl.backend.auth.controller;

import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.auth.session.SessionPublicId;
import gg.modl.proto.modl.v1.PanelAuthResponse;
import gg.modl.proto.modl.v1.PanelPermissionsResponse;
import gg.modl.proto.modl.v1.PanelProfileResponse;
import gg.modl.proto.modl.v1.PanelSessionInfoResponse;
import gg.modl.proto.modl.v1.PanelSessionsResponse;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalString;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;

final class PanelAuthProtoMapper {

    private PanelAuthProtoMapper() {
    }

    static PanelAuthResponse toAuthResponse(boolean success, String message) {
        return PanelAuthResponse.newBuilder()
            .setSuccess(success)
            .setMessage(stringValue(message))
            .build();
    }

    static PanelProfileResponse toProfileResponse(String id, String email, String username, String role,
                                                  String minecraftUsername, String language, String dateFormat) {
        PanelProfileResponse.Builder builder = PanelProfileResponse.newBuilder()
            .setEmail(stringValue(email))
            .setUsername(stringValue(username))
            .setRole(stringValue(role))
            .setMinecraftUsername(stringValue(minecraftUsername))
            .setLanguage(stringValue(language))
            .setDateFormat(stringValue(dateFormat));
        setOptionalString(builder::setId, id);
        return builder.build();
    }

    static PanelPermissionsResponse toPermissionsResponse(List<String> permissions) {
        return PanelPermissionsResponse.newBuilder()
            .addAllPermissions(permissions)
            .build();
    }

    static PanelSessionsResponse toSessionsResponse(List<AuthSessionData> sessions, String currentSessionId) {
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        PanelSessionsResponse.Builder builder = PanelSessionsResponse.newBuilder();
        for (AuthSessionData session : sessions) {
            builder.addSessions(toSessionInfo(session, currentSessionId, isoFormat));
        }
        return builder.build();
    }

    private static PanelSessionInfoResponse toSessionInfo(AuthSessionData session, String currentSessionId,
                                                          SimpleDateFormat isoFormat) {
        PanelSessionInfoResponse.Builder builder = PanelSessionInfoResponse.newBuilder()
            .setId(stringValue(SessionPublicId.of(session.getId())))
            .setIpAddress(stringValue(session.getIpAddress()))
            .setUserAgent(stringValue(session.getUserAgent()))
            .setIsCurrent(session.getId().equals(currentSessionId));
        setOptionalString(builder::setCreatedAt, formatIso(session.getCreatedAt(), isoFormat));
        setOptionalString(builder::setExpiresAt, formatIso(session.getExpiresAt(), isoFormat));
        return builder.build();
    }

    private static String formatIso(Date date, SimpleDateFormat isoFormat) {
        return date != null ? isoFormat.format(date) : null;
    }
}
