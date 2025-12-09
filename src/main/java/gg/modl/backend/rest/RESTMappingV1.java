package gg.modl.backend.rest;

public final class RESTMappingV1 {
    private static final String V1 = "/v1";
    public static final String PREFIX_PANEL = V1 + "/panel";
    public static final String PREFIX_ADMIN = V1 + "/admin";
    public static final String PREFIX_PUBLIC = V1 + "/public";
    public static final String PREFIX_MINECRAFT = V1 + "/minecraft";

    private static final String SERVER = "/server";
    public static final String PUBLIC_SERVER = PREFIX_PUBLIC + SERVER;
    public static final String PANEL_SERVER = PREFIX_PANEL + SERVER;

    private static final String PLAYER = "/player";
    public static final String MINECRAFT_PLAYER = PREFIX_MINECRAFT + PLAYER;

    private static final String AUTH = "/auth";
    public static final String PANEL_AUTH = PREFIX_PANEL + AUTH;
}
