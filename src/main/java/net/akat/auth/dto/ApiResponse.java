package net.akat.auth.dto;

import com.fasterxml.jackson.jr.ob.JSON;

import java.util.HashMap;
import java.util.Map;

public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String error;
    private String message;

    private ApiResponse(boolean success, T data, String error, String message) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.message = message;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, null, message);
    }

    public static <T> ApiResponse<T> error(String error) {
        return new ApiResponse<>(false, null, error, null);
    }

    public static <T> ApiResponse<T> error(String error, String message) {
        return new ApiResponse<>(false, null, error, message);
    }

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public String getError() { return error; }
    public String getMessage() { return message; }

    public String toJson() {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("success", this.success);
            map.put("data", this.data);
            map.put("error", this.error);
            map.put("message", this.message);
            return JSON.std.asString(map);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"JSON_SERIALIZATION_ERROR\",\"message\":\"Failed to serialize response\"}";
        }
    }
}
