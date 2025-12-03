package gg.modl.backend.rest;

import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;

public final class RequestUtil {
    public static Server getRequestServer(HttpServletRequest request) {
        return (Server) request.getAttribute(RequestAttribute.SERVER);
    }
}
