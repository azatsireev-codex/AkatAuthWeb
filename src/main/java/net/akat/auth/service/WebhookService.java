package net.akat.auth.service;

import net.akat.auth.config.PluginConfig;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class WebhookService {
    private final PluginConfig config;
    private final Logger logger;
    private final HttpClient httpClient;

    public WebhookService(PluginConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getWebsiteTimeoutSeconds()))
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    /**
     * Плагин отправляет запрос на сайт для подтверждения успешной регистрации
     * @param nickname ник игрока
     * @return true если сайт ответил 200 OK, false если 404 или ошибка
     */
    public CompletableFuture<Boolean> sendApprovalToWebsite(String nickname) {
        try {
            // Формируем JSON для сайта
            String json = String.format("{\"nickname\":\"%s\"}", nickname);

            // Создаём запрос к сайту
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getWebsiteApprovalUrl()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getWebsiteApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            // Отправляем запрос асинхронно
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        int statusCode = response.statusCode();

                        if (statusCode == 200) {
                            logger.info("✅ Сайт подтвердил получение уведомления успешной регистрации: {} (200 OK)", nickname);
                            return true;
                        } else if (statusCode == 404) {
                            logger.warn("⚠️ Сайт сообщил: регистрация не найдена или истекла: {} (404)", nickname);
                            return false;
                        } else {
                            logger.error("❌ Сайт вернул неожиданный код на этапе успешной регистрации: {} для {}", statusCode, nickname);
                            return false;
                        }
                    })
                    .exceptionally(e -> {
                        logger.error("❌ Ошибка при отправке уведомления на сайт на этапе успешной регистрации: {}", e.getMessage());
                        return false;
                    });

        } catch (Exception e) {
            logger.error("❌ Ошибка при формировании запроса к сайту на этапе успешной регистрации", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Асинхронное уведомление сайта о новом IP
     * Не блокирует поток игрока
     */
    public CompletableFuture<Boolean> notifyNewIpToWebsiteAsync(String nickname, String ipAddress) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = config.getWebsiteUrl() + "/internal/players/verify";

                String json = String.format(
                        "{\"nickname\":\"%s\",\"ipAddress\":\"%s\"}",
                        nickname, ipAddress
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + config.getWebsiteApiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                // Асинхронная отправка
                return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenApply(response -> {
                            int statusCode = response.statusCode();

                            if (statusCode == 200) {
                                logger.info("✅ Сайт получил уведомление о новом IP для {}: {} OK",
                                        nickname, statusCode);
                                return true;
                            } else if (statusCode == 404) {
                                logger.warn("⚠️ Сайт не нашел аккаунт для {} на этапе подтверждения IP: {} Not Found",
                                        nickname, statusCode);
                                return false;
                            } else {
                                logger.error("❌ Сайт вернул ошибку для {} на этапе подтверждения IP: {}", nickname, statusCode);
                                return false;
                            }
                        })
                        .exceptionally(e -> {
                            logger.error("❌ Ошибка при отправке уведомления на этапе подтверждения IP для {}: {}",
                                    nickname, e.getMessage());
                            return false;
                        })
                        .join();

            } catch (Exception e) {
                logger.error("❌ Ошибка при формировании запроса на этапе подтверждения IP для {}: {}", nickname, e.getMessage());
                return false;
            }
        });
    }
}
