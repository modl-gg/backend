package gg.modl.backend.auth.session;

import gg.modl.backend.infrastructure.util.DigestUtils;

public final class SessionPublicId {

    private SessionPublicId() {
    }

    public static String of(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return DigestUtils.sha256Hex(sessionId);
    }
}
