package net.akat.auth.api.handler;

import com.fasterxml.jackson.jr.ob.JSON;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import net.akat.auth.config.PluginConfig;
import net.akat.auth.dto.RegistrationRequest;
import net.akat.auth.dto.RegistrationResponse;
import net.akat.auth.service.RegistrationService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class ApiRequestHandler implements Handler {
    private final RegistrationService registrationService;
    private final PluginConfig config;
    private final Logger logger;

    public ApiRequestHandler(RegistrationService registrationService,
                             PluginConfig config, // ← ДОБАВЬТЕ
                             Logger logger) {
        this.registrationService = registrationService;
        this.config = config;
        this.logger = logger;
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String path = ctx.path();

        if (!checkAuthorization(ctx)) {
            return;
        }

        switch (path) {
            case "/internal/players/account/verify":
                handleRegistration(ctx);
                break;
            case "/internal/connection-requests/approve":
                handleConnectionApprove(ctx);
                break;
            case "/internal/players/ip/check":
                handleRegistrationPrecheck(ctx);
                break;
            default:
                sendErrorResponse(ctx, 404, "NOT_FOUND", "Endpoint not found");
        }
    }

    private boolean checkAuthorization(Context ctx) {
        String authHeader = ctx.header("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(ctx, 401, "UNAUTHORIZED", "Требуется авторизация");
            return false;
        }

        String token = authHeader.substring(7);
        if (!token.equals(config.getApiKey())) {
            sendErrorResponse(ctx, 403, "FORBIDDEN", "Неверный API ключ");
            return false;
        }
        return true;
    }

    private void handleRegistration(Context ctx) {
        try {
            RegistrationRequest request = JSON.std.beanFrom(
                    RegistrationRequest.class, ctx.body()
            );

            RegistrationResponse response = registrationService.processRegistration(request);

            if (response.getStatus().equals("error")) {
                sendErrorResponse(ctx, 409, response.getError(), response.getMessage());
            } else {
                sendSuccessResponse(ctx, response, response.getMessage());
            }

        } catch (Exception e) {
            logger.error("Ошибка обработки регистрации", e);
            sendErrorResponse(ctx, 500, "INTERNAL_ERROR", "Сервис временно недоступен");
        }
    }

    private void handleRegistrationPrecheck(Context ctx) {
        try {
            RegistrationRequest request = JSON.std.beanFrom(
                    RegistrationRequest.class, ctx.body()
            );

            RegistrationResponse validationResult = registrationService.validateRegistration(request);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("nickname", request.getNickname());
            responseData.put("email", request.getEmail());
            responseData.put("ipAddress", request.getIpAddress());

            if (validationResult != null) {
                responseData.put("canRegister", false);
                responseData.put("error", validationResult.getError());
                responseData.put("conflict", validationResult.getConflict());

                sendSuccessResponse(ctx, responseData, validationResult.getMessage());
                return;
            }

            responseData.put("canRegister", true);
            sendSuccessResponse(ctx, responseData, "Поля регистрации прошли валидацию");

        } catch (Exception e) {
            logger.error("Ошибка pre-check регистрации", e);
            sendErrorResponse(ctx, 500, "INTERNAL_ERROR", "Сервис временно недоступен");
        }
    }

    private void handleConnectionApprove(Context ctx) {
        try {
            Map<String, Object> request = JSON.std.mapFrom(ctx.body());
            String nickname = (String) request.get("nickname");
            String ipAddress = (String) request.get("ipAddress");

            if (nickname == null || nickname.trim().isEmpty() ||
                    ipAddress == null || ipAddress.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "BAD_REQUEST", "Поля 'nickname' и 'ipAddress' обязательны");
                return;
            }

            boolean success = registrationService.confirmNewIp(nickname, ipAddress);
            if (success) {
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("nickname", nickname);
                responseData.put("ipAddress", ipAddress);
                responseData.put("confirmedAt", System.currentTimeMillis());

                sendSuccessResponse(ctx, responseData, "IP успешно подтверждён");
            } else {
                sendErrorResponse(ctx, 404, "CONFIRMATION_EXPIRED", "Время подтверждения запроса истекло");
            }

        } catch (Exception e) {
            sendErrorResponse(ctx, 500, "INTERNAL_ERROR", "Сервис временно недоступен");
        }
    }

    private void sendErrorResponse(Context ctx, int status, String errorCode, String message) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", errorCode);
            response.put("message", message);

            ctx.status(status)
                    .contentType("application/json")
                    .result(JSON.std.asString(response));
        } catch (Exception e) {
            logger.error("Ошибка отправки ответа", e);
            ctx.status(status).result("{\"error\":\"" + errorCode + "\"}");
        }
    }

    private void sendSuccessResponse(Context ctx, Object data, String message) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            response.put("message", message);

            ctx.contentType("application/json")
                    .result(JSON.std.asString(response));
        } catch (Exception e) {
            logger.error("Ошибка отправки успешного ответа", e);
            ctx.status(500).result("{\"error\":\"RESPONSE_ERROR\"}");
        }
    }
}
