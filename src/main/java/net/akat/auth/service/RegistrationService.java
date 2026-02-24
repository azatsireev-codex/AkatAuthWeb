package net.akat.auth.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.akat.auth.config.PluginConfig;
import net.akat.auth.dto.RegistrationRequest;
import net.akat.auth.dto.RegistrationResponse;
import net.akat.auth.model.PendingRegistration;
import net.akat.auth.model.Player;
import net.akat.auth.repository.IpAnalyticsRepository;
import net.akat.auth.repository.IpConfirmationRepository;
import net.akat.auth.repository.FamilyAccessRepository;
import net.akat.auth.repository.PendingRegistrationRepository;
import net.akat.auth.repository.PlayerRepository;
import net.akat.auth.service.validator.RegistrationValidator;
import net.akat.auth.service.validator.ValidatorFactory;
import org.slf4j.Logger;

import java.util.List;

@Singleton
public class RegistrationService {
    private final PlayerRepository playerRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final IpConfirmationRepository ipConfirmationRepository;
    private final PluginConfig config;
    private final Logger logger;
    private final List<RegistrationValidator> validators;
    private final WebhookService webhookService;
    private final IpAnalyticsRepository ipAnalyticsRepository;
    private final FamilyAccessRepository familyAccessRepository;

    @Inject
    public RegistrationService(
            PlayerRepository playerRepository,
            PendingRegistrationRepository pendingRegistrationRepository,
            IpConfirmationRepository ipConfirmationRepository,
            IpAnalyticsRepository ipAnalyticsRepository,
            FamilyAccessRepository familyAccessRepository,
            PluginConfig config,
            Logger logger,
            WebhookService webhookService) {
        this.ipAnalyticsRepository = ipAnalyticsRepository;
        this.familyAccessRepository = familyAccessRepository;
        this.playerRepository = playerRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.ipConfirmationRepository = ipConfirmationRepository;
        this.config = config;
        this.logger = logger;
        this.validators = ValidatorFactory.createAllValidators();
        this.webhookService = webhookService;
    }

    public RegistrationResponse processRegistration(RegistrationRequest request) {
        RegistrationResponse validationResult = validateRegistration(request);
        if (validationResult != null) {
            return validationResult;
        }

        // Создаём ожидающую регистрацию
        PendingRegistration pendingRegistration = new PendingRegistration.Builder()
                .nickname(request.getNickname())
                .email(request.getEmail())
                .ipAddress(request.getIpAddress())
                .withTimeoutSeconds(config.getRegistrationTimeoutSeconds())
                .build();

        // Сохраняем
        pendingRegistrationRepository.save(pendingRegistration);

        logger.info("Регистрация ожидает подтверждения: {} (5 мин)", request.getNickname());
        return RegistrationResponse.successPending();
    }

    public RegistrationResponse validateRegistration(RegistrationRequest request) {
        for (RegistrationValidator validator : validators) {
            RegistrationValidator.ValidationResult result = validator.validate(request, playerRepository);
            if (!result.isValid()) {
                if ("IP_IN_USE".equals(result.getErrorCode()) && result.getConflict() != null) {
                    boolean sameFamily = familyAccessRepository.areInSameGroup(
                            request.getNickname(),
                            result.getConflict()
                    );

                    if (sameFamily) {
                        continue;
                    }
                }

                if (result.getConflict() != null) {
                    return RegistrationResponse.errorWithConflict(
                            result.getErrorCode(),
                            result.getErrorMessage(),
                            result.getConflict()
                    );
                }
                return RegistrationResponse.error(result.getErrorCode(), result.getErrorMessage());
            }
        }

        return null;
    }

    public boolean completeRegistration(String nickname, String ipAddress) {
        PendingRegistration pending = pendingRegistrationRepository.findByNickname(nickname);

        if (pending == null || pending.isExpired()) {
            return false;
        }

        // Проверяем IP (Template Method мог бы быть здесь)
        if (config.isStrictIpCheck() && !pending.getIpAddress().equals(ipAddress)) {
            return false;
        }

        // Создаём игрока
        Player player = new Player.Builder()
                .nickname(nickname)
                .email(pending.getEmail())
                .originalIp(pending.getIpAddress())
                .lastIp(ipAddress)
                .build();

        // Сохраняем
        ipAnalyticsRepository.confirmOrCreateRecord(nickname, ipAddress);
        playerRepository.save(player);
        pendingRegistrationRepository.delete(nickname);

        webhookService.sendApprovalToWebsite(nickname)
                .thenAccept(success -> {});

        return true;
    }

    /**
     * Проверяет, зарегистрирован ли игрок и нужно ли отправить уведомление о новом IP
     * @return true если игрок зарегистрирован, false если нет
     */
    public boolean checkAndNotifyNewIp(String nickname, String currentIp) {
        try {
            if (!isPlayerRegistered(nickname)) {
                return false;
            }

            Player player = playerRepository.findByNickname(nickname);
            if (player == null) {
                return false;
            }

            ipAnalyticsRepository.logOrUpdateTimestamp(nickname, currentIp);

            String originalIp = player.getOriginalIp();
            if (originalIp.equals(currentIp)) {
                return true;
            }

            boolean hasActive = ipConfirmationRepository.hasActiveConfirmation(nickname);
            if (hasActive) {
                String expectedIp = ipConfirmationRepository.getCurrentNewIp(nickname);

                if (expectedIp != null && expectedIp.equals(currentIp)) {
                    return false;
                } else {
                    ipConfirmationRepository.createOrUpdateConfirmation(nickname, currentIp, originalIp);
                }
            } else {
                ipConfirmationRepository.createOrUpdateConfirmation(nickname, currentIp, originalIp);
            }

            webhookService.notifyNewIpToWebsiteAsync(nickname, currentIp)
                    .thenAccept(websiteNotified -> {})
                    .exceptionally(e -> {
                        return null;
                    });
            return false;

        } catch (Exception e) {
            logger.error("Ошибка при проверке нового IP для {}", nickname, e);
            return false;
        }
    }

    /**
     * Подтверждает новый IP (вызывается когда сайт подтверждает)
     */
    public boolean confirmNewIp(String nickname, String ipAddressFromRequest) {
        try {
            String newIp = ipConfirmationRepository.confirmByNickname(nickname);

            if (newIp == null) {
                return false;
            }

            Player player = playerRepository.findByNickname(nickname);
            if (player == null) {
                return false;
            }

            ipAnalyticsRepository.confirmOrCreateRecord(nickname, newIp);
            playerRepository.updateOriginalIp(nickname, newIp);
            return true;

        } catch (Exception e) {
            logger.error("❌ Ошибка подтверждения IP для {}", nickname, e);
            return false;
        }
    }

    /**
     * Проверяет, есть ли ожидающая регистрация в БД
     * Возвращает время создания или null
     */
    public Long getPendingRegistrationTime(String nickname) {
        try {
            PendingRegistration pending = pendingRegistrationRepository.findByNickname(nickname);

            if (pending == null) {
                return null;
            }

            return pending.getCreatedAt().toEpochMilli();

        } catch (Exception e) {
            logger.error("Ошибка при получении времени регистрации для {}", nickname, e);
            return null;
        }
    }

    /**
     * Удаляет истекшую регистрацию
     */
    public void removeExpiredRegistration(String nickname) {
        try {
            PendingRegistration pending = pendingRegistrationRepository.findByNickname(nickname);

            if (pending != null && pending.isExpired()) {
                pendingRegistrationRepository.delete(nickname);
                logger.debug("Удалена истекшая регистрация для {}", nickname);
            }
        } catch (Exception e) {
            logger.error("Ошибка при удалении истекшей регистрации", e);
        }
    }

    public void cleanupExpiredIpConfirmations() {
        try {
            int deleted = ipConfirmationRepository.cleanupExpired();
            if (deleted > 0) {
                logger.info("Очищено {} истекших подтверждений IP", deleted);
            }
        } catch (Exception e) {
            logger.error("Ошибка при очистке истекших подтверждений IP", e);
        }
    }

    public void cleanupOldRegistrations(int hours) {
        try {
            pendingRegistrationRepository.cleanupOlderThanHours(hours);
        } catch (Exception e) {
            logger.error("Ошибка при очистке старых регистраций", e);
        }
    }

    public boolean isPlayerRegistered(String nickname) {
        return playerRepository.existsByNickname(nickname);
    }
}
