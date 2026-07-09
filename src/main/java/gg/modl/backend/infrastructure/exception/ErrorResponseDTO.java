package gg.modl.backend.infrastructure.exception;

public record ErrorResponseDTO(int status, String error, String message) {
    public ErrorResponseDTO(int status, String error) {
        this(status, error, error);
    }
}
