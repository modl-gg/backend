package gg.modl.backend.server.data;

// Enum names have to be lowercase to work with current MongoDB data. Make these uppercase once database has been modified.
public enum ProvisioningStatus {
    pending,
    in_progress,
    completed,
    failed
}
