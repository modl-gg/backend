package gg.modl.backend.limits;

public final class BetaLimits {
    public static final long MAX_STAFF_SEATS = 3;
    public static final long MAX_STORAGE_BYTES = 100L * 1024 * 1024;
    public static final boolean AI_MODERATION_ENABLED = true;
    public static final long AI_REQUEST_LIMIT = 50;
    public static final boolean CUSTOM_DOMAIN_ALLOWED = false;
    public static final long MIGRATION_FILE_SIZE_LIMIT = 0;
    public static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;

    private BetaLimits() {
    }
}
