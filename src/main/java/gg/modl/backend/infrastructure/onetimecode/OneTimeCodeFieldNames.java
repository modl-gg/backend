package gg.modl.backend.infrastructure.onetimecode;

public record OneTimeCodeFieldNames(String codeHash, String expiresAt, String failedAttempts) {
}
