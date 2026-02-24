package net.akat.auth.config;

import com.google.inject.Singleton;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class PluginConfig {
    private static final int DEFAULT_REGISTRATION_TIMEOUT_SECONDS = 300;
    private static final int DEFAULT_API_PORT = 8668;
    private static final String DEFAULT_API_KEY = "change-me-api-key";
    private static final boolean DEFAULT_STRICT_IP_CHECK = true;
    private static final String DEFAULT_WEBSITE_URL = "http://127.0.0.1:8998";
    private static final String DEFAULT_WEBSITE_API_PATH = "/internal/players/account/approve";
    private static final String DEFAULT_WEBSITE_API_KEY = "change-me-website-api-key";
    private static final int DEFAULT_WEBSITE_TIMEOUT_SECONDS = 10;
    private static final String DEFAULT_WEBSITE_NEW_IP_PATH = "/internal/players/verify";

    private final Path configPath;

    private int registrationTimeoutSeconds = DEFAULT_REGISTRATION_TIMEOUT_SECONDS;
    private int apiPort = DEFAULT_API_PORT;
    private String apiKey = DEFAULT_API_KEY;
    private boolean strictIpCheck = DEFAULT_STRICT_IP_CHECK;

    private String websiteUrl = DEFAULT_WEBSITE_URL;
    private String websiteApiPath = DEFAULT_WEBSITE_API_PATH;
    private String websiteApiKey = DEFAULT_WEBSITE_API_KEY;
    private int websiteTimeoutSeconds = DEFAULT_WEBSITE_TIMEOUT_SECONDS;
    private String websiteNewIpPath = DEFAULT_WEBSITE_NEW_IP_PATH;

    public PluginConfig(Path configPath) {
        this.configPath = configPath;
    }

    public synchronized void load(Logger logger) {
        try {
            if (Files.notExists(configPath)) {
                saveDefaults();
                logger.info("Создан файл конфигурации: {}", configPath);
            }

            Map<String, String> values = parseYamlLikeFile(Files.readAllLines(configPath));
            registrationTimeoutSeconds = parseInt(values.get("registrationTimeoutSeconds"), DEFAULT_REGISTRATION_TIMEOUT_SECONDS);
            apiPort = parseInt(values.get("apiPort"), DEFAULT_API_PORT);
            apiKey = valueOrDefault(values.get("apiKey"), DEFAULT_API_KEY);
            strictIpCheck = parseBoolean(values.get("strictIpCheck"), DEFAULT_STRICT_IP_CHECK);

            websiteUrl = valueOrDefault(values.get("websiteUrl"), DEFAULT_WEBSITE_URL);
            websiteApiPath = valueOrDefault(values.get("websiteApiPath"), DEFAULT_WEBSITE_API_PATH);
            websiteApiKey = valueOrDefault(values.get("websiteApiKey"), DEFAULT_WEBSITE_API_KEY);
            websiteTimeoutSeconds = parseInt(values.get("websiteTimeoutSeconds"), DEFAULT_WEBSITE_TIMEOUT_SECONDS);
            websiteNewIpPath = valueOrDefault(values.get("websiteNewIpPath"), DEFAULT_WEBSITE_NEW_IP_PATH);

        } catch (IOException e) {
            logger.error("Не удалось загрузить конфиг: {}", configPath, e);
        }
    }

    private void saveDefaults() throws IOException {
        List<String> lines = List.of(
                "# WebAuth plugin config",
                "registrationTimeoutSeconds: " + DEFAULT_REGISTRATION_TIMEOUT_SECONDS,
                "apiPort: " + DEFAULT_API_PORT,
                "apiKey: \"" + DEFAULT_API_KEY + "\"",
                "strictIpCheck: " + DEFAULT_STRICT_IP_CHECK,
                "websiteUrl: \"" + DEFAULT_WEBSITE_URL + "\"",
                "websiteApiPath: \"" + DEFAULT_WEBSITE_API_PATH + "\"",
                "websiteApiKey: \"" + DEFAULT_WEBSITE_API_KEY + "\"",
                "websiteTimeoutSeconds: " + DEFAULT_WEBSITE_TIMEOUT_SECONDS,
                "websiteNewIpPath: \"" + DEFAULT_WEBSITE_NEW_IP_PATH + "\""
        );
        Files.write(configPath, lines);
    }

    private Map<String, String> parseYamlLikeFile(List<String> lines) {
        Map<String, String> values = new HashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains(":")) {
                continue;
            }

            String[] parts = trimmed.split(":", 2);
            String key = parts[0].trim();
            String value = parts[1].trim();

            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }

            values.put(key, value);
        }
        return values;
    }

    private int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean parseBoolean(String value, boolean fallback) {
        if (value == null) return fallback;
        return Boolean.parseBoolean(value);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public int getRegistrationTimeoutSeconds() { return registrationTimeoutSeconds; }
    public int getApiPort() { return apiPort; }
    public String getApiKey() { return apiKey; }
    public boolean isStrictIpCheck() { return strictIpCheck; }

    public String getWebsiteUrl() { return websiteUrl; }
    public String getWebsiteApiPath() { return websiteApiPath; }
    public String getWebsiteApiKey() { return websiteApiKey; }
    public int getWebsiteTimeoutSeconds() { return websiteTimeoutSeconds; }
    public String getWebsiteApprovalUrl() {
        return websiteUrl + websiteApiPath;
    }

    public String getWebsiteNewIpPath() { return websiteNewIpPath; }
    public String getWebsiteNewIpUrl() {
        return websiteUrl + websiteNewIpPath;
    }
}
