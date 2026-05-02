package gg.modl.backend.realtime.auth;

import gg.modl.backend.server.data.Server;
import org.jetbrains.annotations.Nullable;

public record RealtimePrincipal(
    RealtimeClientKind clientKind,
    Server server,
    @Nullable String email,
    @Nullable String serverInstanceId
) {
    public static RealtimePrincipal panel(Server server, String email) {
        return new RealtimePrincipal(RealtimeClientKind.PANEL, server, email, null);
    }

    public static RealtimePrincipal minecraft(Server server) {
        return minecraft(server, null);
    }

    public static RealtimePrincipal minecraft(Server server, @Nullable String serverInstanceId) {
        return new RealtimePrincipal(RealtimeClientKind.MINECRAFT, server, null, serverInstanceId);
    }

    public String serverId() {
        return server.getId();
    }
}
