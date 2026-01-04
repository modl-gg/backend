package gg.modl.backend;

public final class ModlConstants {
    private ModlConstants() {}

    public static final String BRAND_NAME = "modl.gg";

    public static final class Domain {
        private Domain() {}

        public static final String BASE = "modl.gg";
        public static final String API = "api.modl.gg";
        public static final String ADMIN = "admin.modl.gg";
        public static final String CDN = "cdn.modl.gg";
        public static final String APP = "app.modl.gg";

        public static final String HTTPS_ADMIN = "https://" + ADMIN;
        public static final String HTTPS_API = "https://" + API;
        public static final String HTTPS_APP = "https://" + APP;
    }

    public static final class Email {
        private Email() {}

        public static final String ADMIN = "admin@modl.gg";
        public static final String SUPPORT = "support@modl.gg";
        public static final String NOREPLY = "noreply@modl.gg";
    }

    public static final class Discord {
        private Discord() {}

        public static final String INVITE_URL = "https://modl.gg/discord";
        public static final String SHORT_URL = "discord.modl.gg";
    }
}
