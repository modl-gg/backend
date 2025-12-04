package gg.modl.backend.server.data;

// Enum names have to be lowercase to work with current MongoDB data. Make these uppercase once database has been modified.
public enum CustomDomainStatus {
    pending,
    error,
    active,
    verifying
}
