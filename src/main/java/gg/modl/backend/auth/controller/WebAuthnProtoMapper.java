package gg.modl.backend.auth.controller;

import gg.modl.backend.auth.WebAuthnService;
import gg.modl.proto.modl.v1.WebAuthnCredentialMetadata;
import gg.modl.proto.modl.v1.WebAuthnCredentialMutationResponse;
import gg.modl.proto.modl.v1.WebAuthnCredentialsResponse;

import java.util.Date;
import java.util.List;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

final class WebAuthnProtoMapper {

    private WebAuthnProtoMapper() {
    }

    static WebAuthnCredentialsResponse toCredentialsResponse(List<WebAuthnService.CredentialInfo> credentials) {
        WebAuthnCredentialsResponse.Builder builder = WebAuthnCredentialsResponse.newBuilder();
        for (WebAuthnService.CredentialInfo credential : credentials) {
            builder.addCredentials(toMetadata(credential));
        }
        return builder.build();
    }

    static WebAuthnCredentialMutationResponse toMutationResponse(boolean success) {
        return WebAuthnCredentialMutationResponse.newBuilder()
            .setSuccess(success)
            .build();
    }

    private static WebAuthnCredentialMetadata toMetadata(WebAuthnService.CredentialInfo credential) {
        WebAuthnCredentialMetadata.Builder builder = WebAuthnCredentialMetadata.newBuilder()
            .setId(stringValue(credential.id()))
            .setName(stringValue(credential.name()));
        Date createdAt = credential.createdAt();
        if (createdAt != null) {
            builder.setCreatedAt(toTimestamp(createdAt));
        }
        Date lastUsedAt = credential.lastUsedAt();
        if (lastUsedAt != null) {
            builder.setLastUsedAt(toTimestamp(lastUsedAt));
        }
        return builder.build();
    }
}
