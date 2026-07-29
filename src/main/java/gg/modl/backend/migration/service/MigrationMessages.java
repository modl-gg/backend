package gg.modl.backend.migration.service;

final class MigrationMessages {
    private MigrationMessages() {
    }

    static String truncate(String value, int maxLength) {
        if (value.length() > maxLength) {
            return value.substring(0, maxLength - 1) + "…";
        }
        return value;
    }
}
