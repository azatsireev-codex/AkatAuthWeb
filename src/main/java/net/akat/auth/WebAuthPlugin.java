package net.akat.auth;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.javalin.Javalin;
import net.akat.auth.api.handler.ApiRequestHandler;
import net.akat.auth.config.PluginConfig;
import net.akat.auth.integration.python.PythonAuthServiceClient;
import net.akat.auth.util.IpUtils;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Plugin(id = "web-auth-plugin", name = "WebAuth", version = "1.0")
public class WebAuthPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private PluginConfig config;
    private PythonAuthServiceClient pythonClient;
    private Javalin httpServer;

    @Inject
    public WebAuthPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            config = new PluginConfig(dataDirectory.resolve("config.yml"));
            config.load(logger);
            pythonClient = new PythonAuthServiceClient(config, logger);

            startHttpServer();
            registerCommands();

            logger.info("✅ WebAuth плагин успешно инициализирован!");
            logger.info("📡 API доступен на порту: {}", config.getApiPort());
            logger.info("🐍 Python service: {}", config.getPythonServiceBaseUrl());

        } catch (Exception e) {
            logger.error("❌ Ошибка при инициализации плагина", e);
            throw new RuntimeException("Failed to initialize WebAuth plugin", e);
        }
    }

    private void registerCommands() {
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("webauthreload").plugin(this).build(),
                (SimpleCommand) invocation -> {
                    CommandSource source = invocation.source();
                    if (!(source.hasPermission("webauth.reload") || source instanceof com.velocitypowered.api.proxy.ConsoleCommandSource)) {
                        source.sendMessage(Component.text("§cНедостаточно прав."));
                        return;
                    }

                    boolean reloaded = reloadConfigAndApply();
                    if (reloaded) {
                        source.sendMessage(Component.text("§aWebAuth конфигурация успешно перезагружена."));
                    } else {
                        source.sendMessage(Component.text("§cОшибка перезагрузки конфигурации. Проверьте консоль."));
                    }
                }
        );

        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("webauthfamily").plugin(this).build(),
                (SimpleCommand) this::handleFamilyCommand
        );
    }

    private void handleFamilyCommand(SimpleCommand.Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof com.velocitypowered.api.proxy.ConsoleCommandSource)) {
            source.sendMessage(Component.text("§cЭта команда доступна только из консоли."));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            source.sendMessage(Component.text("§eИспользование: /webauthfamily <create|add|remove|delete> ..."));
            return;
        }

        try {
            String payload;
            PythonAuthServiceClient.ProxyResponse response;
            switch (args[0].toLowerCase()) {
                case "create":
                    if (args.length < 3) {
                        source.sendMessage(Component.text("§eИспользование: /webauthfamily create <nickname1> <nickname2>"));
                        return;
                    }
                    payload = String.format("{\"nickname1\":\"%s\",\"nickname2\":\"%s\"}", args[1], args[2]);
                    response = pythonClient.postWithPythonAuth("/family/create", payload);
                    break;
                case "add":
                    if (args.length < 3) {
                        source.sendMessage(Component.text("§eИспользование: /webauthfamily add <groupId> <nickname>"));
                        return;
                    }
                    payload = String.format("{\"groupId\":\"%s\",\"nickname\":\"%s\"}", args[1], args[2]);
                    response = pythonClient.postWithPythonAuth("/family/add", payload);
                    break;
                case "remove":
                    if (args.length < 2) {
                        source.sendMessage(Component.text("§eИспользование: /webauthfamily remove <nickname>"));
                        return;
                    }
                    payload = String.format("{\"nickname\":\"%s\"}", args[1]);
                    response = pythonClient.postWithPythonAuth("/family/remove", payload);
                    break;
                case "delete":
                    if (args.length < 2) {
                        source.sendMessage(Component.text("§eИспользование: /webauthfamily delete <groupId>"));
                        return;
                    }
                    payload = String.format("{\"groupId\":\"%s\"}", args[1]);
                    response = pythonClient.postWithPythonAuth("/family/delete", payload);
                    break;
                default:
                    source.sendMessage(Component.text("§eНеизвестная подкоманда. Доступно: create, add, remove, delete"));
                    return;
            }

            source.sendMessage(Component.text(response.statusCode < 400
                    ? "§aОперация выполнена: " + response.body
                    : "§cОшибка: " + response.body));

        } catch (Exception e) {
            logger.error("Ошибка выполнения команды family", e);
            source.sendMessage(Component.text("§cОшибка выполнения команды. Подробности в консоли."));
        }
    }

    private synchronized boolean reloadConfigAndApply() {
        try {
            stopHttpServer();
            config.load(logger);
            pythonClient.reloadFromConfig();
            startHttpServer();
            logger.info("✅ Конфигурация WebAuth перезагружена. Новый API порт: {}", config.getApiPort());
            return true;
        } catch (Exception e) {
            logger.error("❌ Ошибка при перезагрузке конфигурации WebAuth", e);
            return false;
        }
    }

    private void startHttpServer() {
        try {
            var apiHandler = new ApiRequestHandler(pythonClient, logger);
            httpServer = Javalin.create(javalinConfig -> {
                javalinConfig.showJavalinBanner = false;
                javalinConfig.http.defaultContentType = "application/json";
            }).start(config.getApiPort());

            httpServer.post("/internal/players/account/verify", apiHandler);
            httpServer.post("/internal/connection-requests/approve", apiHandler);
            httpServer.post("/internal/players/ip/check", apiHandler);

            logger.info("✅ HTTP сервер запущен на порту {}", config.getApiPort());

        } catch (Exception e) {
            logger.error("❌ Ошибка при запуске HTTP сервера", e);
            throw new RuntimeException("Failed to start HTTP server", e);
        }
    }

    private void stopHttpServer() {
        if (httpServer == null) {
            return;
        }

        try {
            httpServer.stop();
            logger.info("ℹ️ HTTP сервер остановлен");
        } catch (Exception e) {
            logger.warn("⚠️ Не удалось корректно остановить HTTP сервер", e);
        }
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getUsername();
        String playerIp = IpUtils.getPlayerIp(player);

        Map<String, Object> decision = pythonClient.checkLogin(playerName, playerIp);
        String code = String.valueOf(decision.getOrDefault("decision", "ERROR"));

        switch (code) {
            case "ALLOW":
                return;
            case "NOT_REGISTERED":
                player.disconnect(createNotRegisteredMessage());
                return;
            case "PENDING_EXPIRED":
                long seconds = ((Number) decision.getOrDefault("timePassedSeconds", 0)).longValue();
                player.disconnect(createExpiredMessage(seconds));
                return;
            case "PENDING_COMPLETE_SUCCESS":
                player.disconnect(createSuccessMessage(playerName));
                return;
            case "PENDING_IP_MISMATCH":
                player.disconnect(createIpMismatchMessage());
                return;
            case "NEW_IP_CONFIRMATION_REQUIRED":
                player.disconnect(createNewIpMessage(playerName, playerIp));
                return;
            default:
                player.disconnect(Component.text(config.getMessageServerError()));
        }
    }

    private Component createNotRegisteredMessage() {
        return Component.text(config.getMessageNotRegistered());
    }

    private Component createExpiredMessage(long timePassedSeconds) {
        String text = config.getMessageExpired()
                .replace("{timePassedSeconds}", String.valueOf(timePassedSeconds));
        return Component.text(text);
    }

    private Component createSuccessMessage(String playerName) {
        String text = config.getMessageSuccess()
                .replace("{playerName}", playerName);
        return Component.text(text);
    }

    private Component createIpMismatchMessage() {
        return Component.text(config.getMessageIpMismatch());
    }

    private Component createNewIpMessage(String playerName, String newIp) {
        String text = config.getMessageNewIp()
                .replace("{playerName}", playerName)
                .replace("{newIp}", newIp);
        return Component.text(text);
    }
}
