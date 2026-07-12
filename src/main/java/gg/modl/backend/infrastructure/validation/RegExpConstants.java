package gg.modl.backend.infrastructure.validation;

public final class RegExpConstants {
    public static final String UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    public static final String MINECRAFT_USERNAME = "^[a-zA-Z0-9_.]{2,16}$";

    private RegExpConstants() {
    }
}
