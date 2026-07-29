package gg.modl.backend.infrastructure.proto;

import gg.modl.backend.infrastructure.rest.RouteGroups;
import gg.modl.proto.modl.v1.ApiError;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class ProtobufErrorResponseWriter {

    public boolean shouldWriteProtobuf(HttpServletRequest request) {
        String path = protobufDecisionPath(request);
        if (RouteGroups.isVersion3Prefix(path)) {
            return true;
        }
        if (RouteGroups.isVersion1Prefix(path) || RouteGroups.isVersion2MinecraftPrefix(path)) {
            return false;
        }
        return acceptsProtobuf(Collections.list(request.getHeaders(HttpHeaders.ACCEPT)));
    }

    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE);
        ApiError.newBuilder()
            .setStatusCode(status)
            .setCode(code)
            .setMessage(message)
            .build()
            .writeTo(response.getOutputStream());
    }

    private boolean acceptsProtobuf(List<String> acceptHeaders) {
        if (acceptHeaders.isEmpty()) {
            return false;
        }
        for (String header : acceptHeaders) {
            try {
                for (MediaType mediaType : MediaType.parseMediaTypes(header)) {
                    if (mediaType.isCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                        || mediaType.isCompatibleWith(ProtobufMediaTypes.APPLICATION_PROTOBUF)) {
                        return true;
                    }
                }
            } catch (InvalidMediaTypeException ignored) {
                return false;
            }
        }
        return false;
    }

    private String protobufDecisionPath(HttpServletRequest request) {
        Object errorPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (errorPath instanceof String originalPath && !originalPath.isBlank()) {
            return originalPath;
        }
        return request.getRequestURI();
    }
}
