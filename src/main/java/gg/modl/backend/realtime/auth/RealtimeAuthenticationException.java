package gg.modl.backend.realtime.auth;

import gg.modl.proto.modl.v1.ErrorCode;

public class RealtimeAuthenticationException extends Exception {
    private final ErrorCode errorCode;

    public RealtimeAuthenticationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
