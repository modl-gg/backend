package gg.modl.backend;

public final class Constants {
    public static final String BRAND_NAME = "modl.gg";

    private Constants() {}

    public static final class Domain {
        public static final String API = "api.modl.gg";
        public static final String ADMIN = "admin.modl.gg";
        public static final String HTTPS_ADMIN = "https://" + ADMIN;
        private Domain() {}
    }

    public static final class Email {
        public static final String ADMIN = "admin@modl.gg";

        private Email() {}
    }
}
