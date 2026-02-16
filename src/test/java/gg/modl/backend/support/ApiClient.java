package gg.modl.backend.support;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class ApiClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String sessionToken;
    private final String serverDomain;
    private final Gson gson = new Gson();

    public ApiClient() {
        this.baseUrl = StagingCredentials.baseUrl();
        this.apiKey = StagingCredentials.apiKey();
        this.sessionToken = StagingCredentials.sessionToken();
        this.serverDomain = StagingCredentials.serverDomain();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // ── Minecraft auth: X-API-Key + X-Server-Domain ──

    public HttpResponse<String> minecraftGet(String path) throws Exception {
        return send(newBuilder(path)
                .header("X-API-Key", apiKey)
                .header("X-Server-Domain", serverDomain)
                .GET()
                .build());
    }

    public HttpResponse<String> minecraftPost(String path, Object body) throws Exception {
        return send(newBuilder(path)
                .header("X-API-Key", apiKey)
                .header("X-Server-Domain", serverDomain)
                .header("Content-Type", "application/json")
                .POST(jsonBody(body))
                .build());
    }

    public HttpResponse<String> minecraftPatch(String path, Object body) throws Exception {
        return send(newBuilder(path)
                .header("X-API-Key", apiKey)
                .header("X-Server-Domain", serverDomain)
                .header("Content-Type", "application/json")
                .method("PATCH", jsonBody(body))
                .build());
    }

    // ── Panel auth: X-Server-Domain + Cookie: MODL_SESSION=<token> ──

    public HttpResponse<String> panelGet(String path) throws Exception {
        return send(panelBuilder(path).GET().build());
    }

    public HttpResponse<String> panelPost(String path, Object body) throws Exception {
        return send(panelBuilder(path)
                .header("Content-Type", "application/json")
                .POST(jsonBody(body))
                .build());
    }

    public HttpResponse<String> panelPut(String path, Object body) throws Exception {
        return send(panelBuilder(path)
                .header("Content-Type", "application/json")
                .PUT(jsonBody(body))
                .build());
    }

    public HttpResponse<String> panelPatch(String path, Object body) throws Exception {
        return send(panelBuilder(path)
                .header("Content-Type", "application/json")
                .method("PATCH", jsonBody(body))
                .build());
    }

    public HttpResponse<String> panelDelete(String path) throws Exception {
        return send(panelBuilder(path).DELETE().build());
    }

    // ── Public: X-Server-Domain only ──

    public HttpResponse<String> publicGet(String path) throws Exception {
        return send(newBuilder(path)
                .header("X-Server-Domain", serverDomain)
                .GET()
                .build());
    }

    public HttpResponse<String> publicPost(String path, Object body) throws Exception {
        return send(newBuilder(path)
                .header("X-Server-Domain", serverDomain)
                .header("Content-Type", "application/json")
                .POST(jsonBody(body))
                .build());
    }

    // ── Raw (no auth) ──

    public HttpResponse<String> rawGet(String path) throws Exception {
        return send(newBuilder(path).GET().build());
    }

    // ── Helpers ──

    private HttpRequest.Builder panelBuilder(String path) {
        return newBuilder(path)
                .header("X-Server-Domain", serverDomain)
                .header("Cookie", "MODL_SESSION=" + sessionToken);
    }

    private HttpRequest.Builder newBuilder(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30));
    }

    private HttpRequest.BodyPublisher jsonBody(Object body) {
        if (body == null) return HttpRequest.BodyPublishers.noBody();
        String json = (body instanceof String s) ? s : gson.toJson(body);
        return HttpRequest.BodyPublishers.ofString(json);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 429) return response;
            // Rate limited — wait and retry
            Thread.sleep(1000L * (attempt + 1));
        }
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
