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
import com.velocitypowered.api.scheduler.Scheduler;
import io.javalin.Javalin;
import net.akat.auth.api.handler.ApiRequestHandler;
import net.akat.auth.integration.python.FamilyAccessGateway;
import net.akat.auth.integration.python.LocalFamilyAccessGateway;
import net.akat.auth.integration.python.RemoteFamilyAccessGateway;
import net.akat.auth.config.PluginConfig;
import net.akat.auth.repository.*;
import net.akat.auth.service.RegistrationService;
import net.akat.auth.service.WebhookService;
import net.akat.auth.util.IpUtils;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;

@Plugin(id = "web-auth-plugin", name = "WebAuth", version = "1.0")
public class WebAuthPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private PluginConfig config;
    private RegistrationService registrationService;
    private WebhookService webhookService;
    private Javalin httpServer;
    private ApiRequestHandler apiHandler;

    private IpAnalyticsRepository ipAnalyticsRepository;
    private FamilyAccessRepository familyAccessRepository;
    private FamilyAccessGateway familyAccessGateway;

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

            String dbPath = dataDirectory.resolve("database.db").toString();
            DatabaseManager databaseManager = new DatabaseManager(dbPath);
            Connection connection = databaseManager.getConnection();

            String analyticsDbPath = dataDirectory.resolve("analytics.db").toString();
            AnalyticsDatabaseManager analyticsDatabaseManager = new AnalyticsDatabaseManager(analyticsDbPath, logger);
            Connection analyticsConnection = analyticsDatabaseManager.getConnection();

            PlayerRepository playerRepository = new PlayerRepository(connection);
            PendingRegistrationRepository pendingRepository = new PendingRegistrationRepository(connection);
            IpConfirmationRepository ipConfirmationRepository = new IpConfirmationRepository(connection, logger);
            familyAccessRepository = new FamilyAccessRepository(connection, logger);
            familyAccessGateway = config.isPythonServiceEnabled()
                    ? new RemoteFamilyAccessGateway(config, logger)
                    : new LocalFamilyAccessGateway(familyAccessRepository);

            ipAnalyticsRepository = new IpAnalyticsRepository(analyticsConnection, logger);

            webhookService = new WebhookService(config, logger);

            registrationService = new RegistrationService(
                    playerRepository,
                    pendingRepository,
                    ipConfirmationRepository,
                    ipAnalyticsRepository,
                    familyAccessGateway,
                    config,
                    logger,
                    webhookService
            );

            startHttpServer();
            registerCommands();
            scheduleCleanupTasks();

            logger.info("✅ WebAuth плагин успешно инициализирован!");
            logger.info("📡 API доступен на порту: {}", config.getApiPort());
            logger.info("🌐 Webhook URL для сайта: {}", config.getWebsiteApprovalUrl());
            logger.info("🧩 Family service mode: {}", config.isPythonServiceEnabled() ? "python-remote" : "local-sqlite");

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
            switch (args[0].toLowerCase()) {
                case "create":
                    if (args.length < 3) {
                        source.sendMessage(Component.text("§eИспользование: /webauthfamily create <nickname1> <nickname2>"));
                        return;
                    }
                    String groupId = familyAccessGateway.createGroup(args[1], args[2]);
                    source.sendMessage(Component.text("§aСоздана family-группа " + groupId + " для " + args[1] + " и " + args[2]));
                    break;
                case "add":
                    if (args.length < 3) {
                        source.sendMessage(Component.text("§eИспользование: /webauthfamily add <groupId> <nickname>"));
                        return;
                    }
                    familyAccessGateway.addMember(args[1], args[2]);
                    source.sendMessage(Component.text("§aИгрок " + args[2] + " добавлен в группу " + args[1]));
                    break;
                case "remove":
                    if (args.length < 2) {
                        source.sendMessage(Component.text("§eИспользование: /webauthfamily remove <nickname>"));
                        return;
                    }
                    boolean removed = familyAccessGateway.removeMember(args[1]);
                    source.sendMessage(Component.text(removed
                            ? "§aИгрок исключён из family-группы: " + args[1]
                            : "§eИгрок не найден в family-группах: " + args[1]));
                    break;
                case "delete":
                    if (args.length < 2) {
                        source.sendMessage(Component.text("§eИспользование: /webauthfamily delete <groupId>"));
                        return;
                    }
                    int deleted = familyAccessGateway.deleteGroup(args[1]);
                    source.sendMessage(Component.text(deleted > 0
                            ? "§aУдалена family-группа " + args[1] + " (участников: " + deleted + ")"
                            : "§eГруппа не найдена: " + args[1]));
                    break;
                default:
                    source.sendMessage(Component.text("§eНеизвестная подкоманда. Доступно: create, add, remove, delete"));
            }
        } catch (Exception e) {
            logger.error("Ошибка выполнения команды family", e);
            source.sendMessage(Component.text("§cОшибка выполнения команды. Подробности в консоли."));
        }
    }

    private synchronized boolean reloadConfigAndApply() {
        try {
            stopHttpServer();
            config.load(logger);
            webhookService.reloadFromConfig();
            familyAccessGateway = config.isPythonServiceEnabled()
                    ? new RemoteFamilyAccessGateway(config, logger)
                    : new LocalFamilyAccessGateway(familyAccessRepository);
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
            apiHandler = new ApiRequestHandler(registrationService, config, logger);
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

        try {
            Long registrationTime = registrationService.getPendingRegistrationTime(playerName);
            if (registrationTime != null) {
                handlePendingRegistration(player, playerName, playerIp, registrationTime);
                return;
            }

            boolean isRegistered = registrationService.isPlayerRegistered(playerName);
            if (!isRegistered) {
                player.disconnect(createNotRegisteredMessage());
                ipAnalyticsRepository.logOrUpdateTimestamp(playerName, playerIp);
                return;
            }

            boolean ipCheckResult = registrationService.checkAndNotifyNewIp(playerName, playerIp);
            if (!ipCheckResult) {
                player.disconnect(createNewIpMessage(playerName, playerIp));
            }

        } catch (Exception e) {
            logger.error("Ошибка при проверке регистрации игрока: {}", playerName, e);
            player.disconnect(Component.text(
                    "⚠️ Ошибка сервера при проверке аккаунта.\n" +
                            "Пожалуйста, попробуйте позже."
            ));
        }
    }

    private void handlePendingRegistration(Player player, String playerName, String playerIp, Long registrationTime) {
        long timePassed = System.currentTimeMillis() - registrationTime;
        long timePassedSeconds = timePassed / 1000;

        if (timePassed > config.getRegistrationTimeoutSeconds() * 1000L) {
            registrationService.removeExpiredRegistration(playerName);
            player.disconnect(createExpiredMessage(timePassedSeconds));
            return;
        }

        try {
            boolean success = registrationService.completeRegistration(playerName, playerIp);

            if (success) {
                player.disconnect(createSuccessMessage(playerName));
                logger.info("✅ Регистрация завершена: {} ({})", playerName, playerIp);
            } else {
                player.disconnect(createIpMismatchMessage());
                logger.warn("❌ IP mismatch: {} (ожидался другой IP)", playerName);
            }

        } catch (Exception e) {
            logger.error("Ошибка при завершении регистрации: {}", playerName, e);
            player.disconnect(Component.text(
                    "❌ Ошибка при завершении регистрации.\n" +
                            "Пожалуйста, обратитесь в поддержку."
            ));
        }
    }

    private void scheduleCleanupTasks() {
        Scheduler scheduler = server.getScheduler();
        scheduler.buildTask(this, () -> {
            try {
                registrationService.cleanupOldRegistrations(3);
            } catch (Exception e) {
                logger.error("Ошибка при очистке старых запросов", e);
            }
        }).repeat(1, TimeUnit.HOURS).schedule();

        scheduler.buildTask(this, () -> {
            try {
                registrationService.cleanupExpiredIpConfirmations();
            } catch (Exception e) {
                logger.error("Ошибка при очистке истекших подтверждений IP", e);
            }
        }).repeat(1, TimeUnit.MINUTES).schedule();
    }

    private Component createNotRegisteredMessage() {
        return Component.text(
                "§c❌ Вы не зарегистрированы!\n" +
                        "§fПожалуйста, зарегистрируйтесь на нашем сайте.\n" +
                        "После регистрации у вас будет 5 минут чтобы зайти на сервер.\n\n" +
                        "§e🔗 Ссылка на сайт: §bwww.neft.games"
        );
    }

    private Component createExpiredMessage(long timePassedSeconds) {
        return Component.text(
                "§e⏰ Время на регистрацию истекло!\n" +
                        "§fВы зашли через " + timePassedSeconds + " секунд.\n" +
                        "Пожалуйста, начните регистрацию заново на сайте.\n\n" +
                        "§cВнимание: У вас всего 5 минут после регистрации на сайте!"
        );
    }

    private Component createSuccessMessage(String playerName) {
        return Component.text(
                "§a✅ Регистрация успешно завершена!\n" +
                        "§fТеперь вы можете войти на сервер и начать играть.\n\n" +
                        "§eВаш ник: §b" + playerName
        );
    }

    private Component createIpMismatchMessage() {
        return Component.text(
                "§c❌ Ошибка регистрации!\n" +
                        "§fIP-адрес игры не совпадает с тем, с которого вы регистрировались на сайте.\n\n" +
                        "§fПожалуйста, зарегистрируйтесь заново."
        );
    }

    private Component createNewIpMessage(String playerName, String newIp) {
        return Component.text(
                "§c⚠ Обнаружен новый IP-адрес!\n\n" +
                        "§fПожалуйста, подтвердите вход с нового IP:\n" +
                        "§b1. §fПерейдите на наш сайт\n" +
                        "§b2. §fЗайдите в свой аккаунт " + playerName + "\n" +
                        "§b3. §fПодтвердите новый IP-адрес\n\n" +
                        "§e🔗 Ссылка на сайт: §bwww.neft.games\n" +
                        "§fПосле подтверждения попробуйте зайти снова."
        );
    }
}
