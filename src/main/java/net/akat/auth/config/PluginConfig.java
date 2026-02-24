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

    private static final String DEFAULT_MESSAGE_NOT_REGISTERED =
            "§c❌ Вы не зарегистрированы!\\n" +
            "§fПожалуйста, зарегистрируйтесь на нашем сайте.\\n" +
            "После регистрации у вас будет 5 минут чтобы зайти на сервер.\\n\\n" +
            "§e🔗 Ссылка на сайт: §bwww.neft.games";

    private static final String DEFAULT_MESSAGE_EXPIRED =
            "§e⏰ Время на регистрацию истекло!\\n" +
            "§fВы зашли через {timePassedSeconds} секунд.\\n" +
            "Пожалуйста, начните регистрацию заново на сайте.\\n\\n" +
            "§cВнимание: У вас всего 5 минут после регистрации на сайте!";

    private static final String DEFAULT_MESSAGE_SUCCESS =
            "§a✅ Регистрация успешно завершена!\\n" +
            "§fТеперь вы можете войти на сервер и начать играть.\\n\\n" +
            "§eВаш ник: §b{playerName}";

    private static final String DEFAULT_MESSAGE_IP_MISMATCH =
            "§c❌ Ошибка регистрации!\\n" +
            "§fIP-адрес игры не совпадает с тем, с которого вы регистрировались на сайте.\\n\\n" +
            "§fПожалуйста, зарегистрируйтесь заново.";

    private static final String DEFAULT_MESSAGE_NEW_IP =
            "§c⚠ Обнаружен новый IP-адрес!\\n\\n" +
            "§fПожалуйста, подтвердите вход с нового IP:\\n" +
            "§b1. §fПерейдите на наш сайт\\n" +
            "§b2. §fЗайдите в свой аккаунт {playerName}\\n" +
            "§b3. §fПодтвердите новый IP-адрес\\n\\n" +
            "§e🔗 Ссылка на сайт: §bwww.neft.games\\n" +
            "§fПосле подтверждения попробуйте зайти снова.";

    private static final String DEFAULT_MESSAGE_SERVER_ERROR =
            "⚠️ Ошибка сервера при проверке аккаунта.\\nПожалуйста, попробуйте позже.";

    private final Path configPath;

    private int apiPort = DEFAULT_API_PORT;
    private String apiKey = DEFAULT_API_KEY;
    private String pythonServiceBaseUrl = DEFAULT_PYTHON_SERVICE_BASE_URL;
    private String pythonServiceApiKey = DEFAULT_PYTHON_SERVICE_API_KEY;

    private String messageNotRegistered = DEFAULT_MESSAGE_NOT_REGISTERED;
    private String messageExpired = DEFAULT_MESSAGE_EXPIRED;
    private String messageSuccess = DEFAULT_MESSAGE_SUCCESS;
    private String messageIpMismatch = DEFAULT_MESSAGE_IP_MISMATCH;
    private String messageNewIp = DEFAULT_MESSAGE_NEW_IP;
    private String messageServerError = DEFAULT_MESSAGE_SERVER_ERROR;

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

            messageNotRegistered = decodeMessage(valueOrDefault(values.get("messageNotRegistered"), DEFAULT_MESSAGE_NOT_REGISTERED));
            messageExpired = decodeMessage(valueOrDefault(values.get("messageExpired"), DEFAULT_MESSAGE_EXPIRED));
            messageSuccess = decodeMessage(valueOrDefault(values.get("messageSuccess"), DEFAULT_MESSAGE_SUCCESS));
            messageIpMismatch = decodeMessage(valueOrDefault(values.get("messageIpMismatch"), DEFAULT_MESSAGE_IP_MISMATCH));
            messageNewIp = decodeMessage(valueOrDefault(values.get("messageNewIp"), DEFAULT_MESSAGE_NEW_IP));
            messageServerError = decodeMessage(valueOrDefault(values.get("messageServerError"), DEFAULT_MESSAGE_SERVER_ERROR));

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
                "pythonServiceApiKey: \"" + DEFAULT_PYTHON_SERVICE_API_KEY + "\"",
                "",
                "# Player messages (use \\n for new lines)",
                "messageNotRegistered: \"" + DEFAULT_MESSAGE_NOT_REGISTERED + "\"",
                "messageExpired: \"" + DEFAULT_MESSAGE_EXPIRED + "\"",
                "messageSuccess: \"" + DEFAULT_MESSAGE_SUCCESS + "\"",
                "messageIpMismatch: \"" + DEFAULT_MESSAGE_IP_MISMATCH + "\"",
                "messageNewIp: \"" + DEFAULT_MESSAGE_NEW_IP + "\"",
                "messageServerError: \"" + DEFAULT_MESSAGE_SERVER_ERROR + "\""
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

    private String decodeMessage(String value) {
        return value.replace("\\n", "\n");
    }

    public int getApiPort() { return apiPort; }
    public String getApiKey() { return apiKey; }
    public String getPythonServiceBaseUrl() { return pythonServiceBaseUrl; }
    public String getPythonServiceApiKey() { return pythonServiceApiKey; }

    public String getMessageNotRegistered() { return messageNotRegistered; }
    public String getMessageExpired() { return messageExpired; }
    public String getMessageSuccess() { return messageSuccess; }
    public String getMessageIpMismatch() { return messageIpMismatch; }
    public String getMessageNewIp() { return messageNewIp; }
    public String getMessageServerError() { return messageServerError; }
}
