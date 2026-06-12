package gg.modl.backend.server.controller;

import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.ProvisioningStatusResponse;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;

final class PanelServerProtoMapper {

    private PanelServerProtoMapper() {
    }

    static ProvisioningStatusResponse toProvisioningStatusResponse(Server server) {
        ProvisioningStatus status = server.getProvisioningStatus() != null
            ? server.getProvisioningStatus() : ProvisioningStatus.PENDING;
        return ProvisioningStatusResponse.newBuilder()
            .setStatus(toProtoStatus(status))
            .setServerName(stringValue(server.getServerName()))
            .setEmailVerified(server.getEmailVerified() != null && server.getEmailVerified())
            .build();
    }

    private static gg.modl.proto.modl.v1.ProvisioningStatus toProtoStatus(ProvisioningStatus status) {
        return switch (status) {
            case PENDING -> gg.modl.proto.modl.v1.ProvisioningStatus.PROVISIONING_STATUS_PENDING;
            case IN_PROGRESS -> gg.modl.proto.modl.v1.ProvisioningStatus.PROVISIONING_STATUS_IN_PROGRESS;
            case COMPLETED -> gg.modl.proto.modl.v1.ProvisioningStatus.PROVISIONING_STATUS_COMPLETED;
            case FAILED -> gg.modl.proto.modl.v1.ProvisioningStatus.PROVISIONING_STATUS_FAILED;
        };
    }
}
