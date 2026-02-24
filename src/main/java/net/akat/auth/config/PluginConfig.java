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
    private static final int DEFAULT_API_PORT = 8668;
    private static final String DEFAULT_API_KEY = "change-me-api-key";
    private static final String DEFAULT_PYTHON_SERVICE_BASE_URL = "http://127.0.0.1:9000";
    private static final String DEFAULT_PYTHON_SERVICE_API_KEY = "change-me-python-service-key";

    private final Path configPath;

    private int apiPort = DEFAULT_API_PORT;
    private String apiKey = DEFAULT_API_KEY;
    private String pythonServiceBaseUrl = DEFAULT_PYTHON_SERVICE_BASE_URL;
    private String pythonServiceApiKey = DEFAULT_PYTHON_SERVICE_API_KEY;

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
            apiPort = parseInt(values.get("apiPort"), DEFAULT_API_PORT);
            apiKey = valueOrDefault(values.get("apiKey"), DEFAULT_API_KEY);
            pythonServiceBaseUrl = valueOrDefault(values.get("pythonServiceBaseUrl"), DEFAULT_PYTHON_SERVICE_BASE_URL);
            pythonServiceApiKey = valueOrDefault(values.get("pythonServiceApiKey"), DEFAULT_PYTHON_SERVICE_API_KEY);

        } catch (IOException e) {
            logger.error("Не удалось загрузить конфиг: {}", configPath, e);
        }
    }

    private void saveDefaults() throws IOException {
        List<String> lines = List.of(
                "# WebAuth plugin config",
                "apiPort: " + DEFAULT_API_PORT,
                "apiKey: \"" + DEFAULT_API_KEY + "\"",
                "pythonServiceBaseUrl: \"" + DEFAULT_PYTHON_SERVICE_BASE_URL + "\"",
                "pythonServiceApiKey: \"" + DEFAULT_PYTHON_SERVICE_API_KEY + "\""
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

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public int getApiPort() { return apiPort; }
    public String getApiKey() { return apiKey; }
    public String getPythonServiceBaseUrl() { return pythonServiceBaseUrl; }
    public String getPythonServiceApiKey() { return pythonServiceApiKey; }
}
