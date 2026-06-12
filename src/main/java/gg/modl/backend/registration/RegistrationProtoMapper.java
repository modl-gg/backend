package gg.modl.backend.registration;

import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.AutoLoginResponse;
import gg.modl.proto.modl.v1.CliRegistrationResponse;
import gg.modl.proto.modl.v1.CliSetupStatusResponse;
import gg.modl.proto.modl.v1.EmailVerificationResponse;
import gg.modl.proto.modl.v1.PublicRegistrationResponse;
import gg.modl.proto.modl.v1.RegistrationApiKeyResponse;
import gg.modl.proto.modl.v1.ServerSummary;
import gg.modl.proto.modl.v1.SetupStatusResponse;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalString;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;

final class RegistrationProtoMapper {

    private RegistrationProtoMapper() {
    }

    static ServerSummary toServerSummary(Server server) {
        return ServerSummary.newBuilder()
            .setId(stringValue(server.getId()))
            .setName(stringValue(server.getServerName()))
            .build();
    }

    static PublicRegistrationResponse toRegistrationResponse(boolean success, String message, Server server) {
        PublicRegistrationResponse.Builder builder = PublicRegistrationResponse.newBuilder()
            .setSuccess(success)
            .setMessage(stringValue(message));
        if (server != null) {
            builder.setServer(toServerSummary(server));
        }
        return builder.build();
    }

    static EmailVerificationResponse toEmailVerificationResponse(boolean success, String message, String subdomain,
                                                                 String autoLoginToken) {
        EmailVerificationResponse.Builder builder = EmailVerificationResponse.newBuilder()
            .setSuccess(success)
            .setMessage(stringValue(message));
        setOptionalString(builder::setSubdomain, subdomain);
        setOptionalString(builder::setAutoLoginToken, autoLoginToken);
        return builder.build();
    }

    static SetupStatusResponse toSetupStatusResponse(String subdomain, String serverName, Boolean emailVerified,
                                                     ProvisioningStatus provisioningStatus, String message) {
        SetupStatusResponse.Builder builder = SetupStatusResponse.newBuilder();
        setOptionalString(builder::setSubdomain, subdomain);
        setOptionalString(builder::setServerName, serverName);
        if (emailVerified != null) {
            builder.setEmailVerified(emailVerified);
        }
        if (provisioningStatus != null) {
            builder.setProvisioningStatus(toProtoStatus(provisioningStatus));
        }
        setOptionalString(builder::setMessage, message);
        return builder.build();
    }

    static AutoLoginResponse toAutoLoginResponse(boolean success, String message, String redirectUrl) {
        AutoLoginResponse.Builder builder = AutoLoginResponse.newBuilder()
            .setSuccess(success)
            .setMessage(stringValue(message));
        setOptionalString(builder::setRedirectUrl, redirectUrl);
        return builder.build();
    }

    static CliRegistrationResponse toCliRegistrationResponse(boolean success, String message, Server server,
                                                             String setupToken) {
        CliRegistrationResponse.Builder builder = CliRegistrationResponse.newBuilder()
            .setSuccess(success)
            .setMessage(stringValue(message));
        if (server != null) {
            builder.setServer(toServerSummary(server));
        }
        setOptionalString(builder::setSetupToken, setupToken);
        return builder.build();
    }

    static CliSetupStatusResponse toCliSetupStatusResponse(boolean success, Boolean emailVerified,
                                                           ProvisioningStatus provisioningStatus, String apiKey,
                                                           String message) {
        CliSetupStatusResponse.Builder builder = CliSetupStatusResponse.newBuilder()
            .setSuccess(success);
        if (emailVerified != null) {
            builder.setEmailVerified(emailVerified);
        }
        if (provisioningStatus != null) {
            builder.setProvisioningStatus(toProtoStatus(provisioningStatus));
        }
        setOptionalString(builder::setApiKey, apiKey);
        setOptionalString(builder::setMessage, message);
        return builder.build();
    }

    static RegistrationApiKeyResponse toApiKeyResponse(boolean success, String apiKey, String panelUrl, String message) {
        RegistrationApiKeyResponse.Builder builder = RegistrationApiKeyResponse.newBuilder()
            .setSuccess(success);
        setOptionalString(builder::setApiKey, apiKey);
        setOptionalString(builder::setPanelUrl, panelUrl);
        setOptionalString(builder::setMessage, message);
        return builder.build();
    }

    static gg.modl.proto.modl.v1.ProvisioningStatus toProtoStatus(ProvisioningStatus status) {
        if (status == null) {
            return gg.modl.proto.modl.v1.ProvisioningStatus.PROVISIONING_STATUS_UNSPECIFIED;
        }
        return switch (status) {
            case PENDING -> gg.modl.proto.modl.v1.ProvisioningStatus.PROVISIONING_STATUS_PENDING;
            case IN_PROGRESS -> gg.modl.proto.modl.v1.ProvisioningStatus.PROVISIONING_STATUS_IN_PROGRESS;
            case COMPLETED -> gg.modl.proto.modl.v1.ProvisioningStatus.PROVISIONING_STATUS_COMPLETED;
            case FAILED -> gg.modl.proto.modl.v1.ProvisioningStatus.PROVISIONING_STATUS_FAILED;
        };
    }
}
