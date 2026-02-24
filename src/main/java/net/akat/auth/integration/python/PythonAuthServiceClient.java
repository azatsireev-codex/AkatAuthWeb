package net.akat.auth.integration.python;

import com.fasterxml.jackson.jr.ob.JSON;
import net.akat.auth.config.PluginConfig;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class PythonAuthServiceClient {
    private final PluginConfig config;
    private final Logger logger;
    private HttpClient client;

    public PythonAuthServiceClient(PluginConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        rebuildClient();
    }

    public synchronized void reloadFromConfig() {
        rebuildClient();
    }

    private synchronized void rebuildClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public ProxyResponse postWithPluginAuth(String path, String requestBody) {
        return post(path, requestBody, config.getApiKey());
    }

    public ProxyResponse postWithPythonAuth(String path, String requestBody) {
        return post(path, requestBody, config.getPythonServiceApiKey());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> checkLogin(String nickname, String ipAddress) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("nickname", nickname);
            body.put("ipAddress", ipAddress);

            ProxyResponse response = postWithPythonAuth("/minecraft/login/check", JSON.std.asString(body));
            if (response.statusCode >= 400) {
                return Map.of("decision", "ERROR");
            }

            Map<String, Object> parsed = JSON.std.mapFrom(response.body);
            Object data = parsed.get("data");
            return data instanceof Map ? (Map<String, Object>) data : Map.of("decision", "ERROR");
        } catch (Exception e) {
            logger.error("Ошибка проверки логина через python service", e);
            return Map.of("decision", "ERROR");
        }
    }

    private ProxyResponse post(String path, String requestBody, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getPythonServiceBaseUrl() + path))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody == null ? "{}" : requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new ProxyResponse(response.statusCode(), response.body());
        } catch (Exception e) {
            logger.error("Ошибка запроса к python service: {}", path, e);
            return new ProxyResponse(500, "{\"success\":false,\"error\":\"PYTHON_UNAVAILABLE\",\"message\":\"Python service unavailable\"}");
        }
    }

    public static class ProxyResponse {
        public final int statusCode;
        public final String body;

        public ProxyResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}
