package gg.modl.backend.beta;

public record BetaApiResponse<T>(boolean success, T data, String message, String error) {
    public static <T> BetaApiResponse<T> ok(T data) {
        return new BetaApiResponse<>(true, data, null, null);
    }

    public static <T> BetaApiResponse<T> error(String error) {
        return new BetaApiResponse<>(false, null, null, error);
    }
}
