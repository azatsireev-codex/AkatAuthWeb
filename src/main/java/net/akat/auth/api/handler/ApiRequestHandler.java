package net.akat.auth.api.handler;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import net.akat.auth.integration.python.PythonAuthServiceClient;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public class ApiRequestHandler implements Handler {
    private final PythonAuthServiceClient pythonClient;
    private final Logger logger;

    public ApiRequestHandler(PythonAuthServiceClient pythonClient, Logger logger) {
        this.pythonClient = pythonClient;
        this.logger = logger;
    }

    @Override
    public void handle(@NotNull Context ctx) {
        String path = ctx.path();

        try {
            PythonAuthServiceClient.ProxyResponse response;
            switch (path) {
                case "/internal/players/account/verify":
                    response = pythonClient.postWithPluginAuth(path, ctx.body());
                    break;
                case "/internal/connection-requests/approve":
                    response = pythonClient.postWithPluginAuth(path, ctx.body());
                    break;
                case "/internal/players/ip/check":
                    response = pythonClient.postWithPluginAuth(path, ctx.body());
                    break;
                default:
                    ctx.status(404).result("{\"success\":false,\"error\":\"NOT_FOUND\",\"message\":\"Endpoint not found\"}");
                    return;
            }

            ctx.status(response.statusCode)
                    .contentType("application/json")
                    .result(response.body);

        } catch (Exception e) {
            logger.error("Ошибка proxy обработки API", e);
            ctx.status(500)
                    .contentType("application/json")
                    .result("{\"success\":false,\"error\":\"INTERNAL_ERROR\",\"message\":\"Сервис временно недоступен\"}");
        }
    }
}
