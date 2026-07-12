package gg.modl.backend.infrastructure.rest;

public final class RouteGroups {

    private RouteGroups() {
    }

    public static boolean isPanelArea(String path) {
        return matchesExactOrChild(path, RESTMappingV1.PREFIX_PANEL);
    }

    public static boolean isAdminArea(String path) {
        return matchesExactOrChild(path, RESTMappingV1.PREFIX_ADMIN);
    }

    public static boolean isPanelAuthArea(String path) {
        return matchesExactOrChild(path, RESTMappingV1.PANEL_AUTH);
    }

    public static boolean isPanelPrefix(String path) {
        return matchesPrefix(path, RESTMappingV1.PREFIX_PANEL);
    }

    public static boolean isMinecraftApiPrefix(String path) {
        return matchesPrefix(path, RESTMappingV1.PREFIX_MINECRAFT)
            || matchesPrefix(path, RESTMappingV2.PREFIX_MINECRAFT)
            || matchesPrefix(path, RESTMappingV3.PREFIX_MINECRAFT);
    }

    public static boolean isMinecraftOrReplayLitePrefix(String path) {
        return isMinecraftApiPrefix(path) || matchesPrefix(path, RESTMappingV1.PREFIX_REPLAY_LITE);
    }

    public static boolean isVersion1Prefix(String path) {
        return matchesPrefix(path, RESTMappingV1.V1);
    }

    public static boolean isVersion2MinecraftPrefix(String path) {
        return matchesPrefix(path, RESTMappingV2.PREFIX_MINECRAFT);
    }

    public static boolean isVersion3Prefix(String path) {
        return matchesPrefix(path, RESTMappingV3.PREFIX);
    }

    public static boolean isPanelChild(String path) {
        return matchesChild(path, RESTMappingV1.PREFIX_PANEL);
    }

    public static boolean isPublicChild(String path) {
        return matchesChild(path, RESTMappingV1.PREFIX_PUBLIC);
    }

    public static boolean isPublicReplayLiteChild(String path) {
        return matchesChild(path, RESTMappingV1.PUBLIC_REPLAY_LITE);
    }

    public static boolean isReplayLiteChild(String path) {
        return matchesChild(path, RESTMappingV1.PREFIX_REPLAY_LITE);
    }

    public static boolean isAdminChild(String path) {
        return matchesChild(path, RESTMappingV1.PREFIX_ADMIN);
    }

    public static boolean isAdminAuthChild(String path) {
        return matchesChild(path, RESTMappingV1.ADMIN_AUTH);
    }

    private static boolean matchesExactOrChild(String path, String base) {
        return path != null && (path.equals(base) || path.startsWith(base + "/"));
    }

    private static boolean matchesChild(String path, String base) {
        return path != null && path.startsWith(base + "/");
    }

    private static boolean matchesPrefix(String path, String base) {
        return path != null && path.startsWith(base);
    }
}
