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

public class RemoteFamilyAccessGateway implements FamilyAccessGateway {
    private final PluginConfig config;
    private final Logger logger;
    private final HttpClient client;

    public RemoteFamilyAccessGateway(PluginConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String createGroup(String nickname1, String nickname2) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("nickname1", nickname1);
        payload.put("nickname2", nickname2);

        Map<String, Object> data = postForData("/family/create", payload);
        Object groupId = data.get("groupId");
        if (groupId == null) {
            throw new RuntimeException("Remote service did not return groupId");
        }
        return groupId.toString();
    }

    @Override
    public void addMember(String groupId, String nickname) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("groupId", groupId);
        payload.put("nickname", nickname);
        postForData("/family/add", payload);
    }

    @Override
    public boolean removeMember(String nickname) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("nickname", nickname);
        Map<String, Object> data = postForData("/family/remove", payload);
        return Boolean.TRUE.equals(data.get("removed"));
    }

    @Override
    public int deleteGroup(String groupId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("groupId", groupId);
        Map<String, Object> data = postForData("/family/delete", payload);
        Object deleted = data.get("deleted");
        return deleted instanceof Number ? ((Number) deleted).intValue() : 0;
    }


    @Override
    public boolean areInSameGroup(String nickname1, String nickname2) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("nickname1", nickname1);
        payload.put("nickname2", nickname2);
        Map<String, Object> data = postForData("/family/check", payload);
        return Boolean.TRUE.equals(data.get("sameGroup"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForData(String path, Map<String, Object> payload) {
        try {
            String body = JSON.std.asString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getPythonServiceBaseUrl() + path))
                    .header("Authorization", "Bearer " + config.getPythonServiceApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> parsed = JSON.std.mapFrom(response.body());
            boolean success = Boolean.TRUE.equals(parsed.get("success"));
            if (!success) {
                throw new RuntimeException("Remote family operation failed: " + parsed.get("message"));
            }

            Object data = parsed.get("data");
            return data instanceof Map ? (Map<String, Object>) data : new HashMap<>();
        } catch (Exception e) {
            logger.error("Ошибка запроса к python family service", e);
            throw new RuntimeException("Remote family service request failed", e);
        }
    }
}
