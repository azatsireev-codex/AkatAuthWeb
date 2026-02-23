package net.akat.auth.dto;

public class RegistrationResponse {
    private String status;
    private String message;
    private Integer timeout;
    private Long timeLeftSeconds;
    private String error;
    private String conflict;

    public RegistrationResponse() {}

    public static RegistrationResponse successPending() {
        RegistrationResponse response = new RegistrationResponse();
        response.setStatus("pending");
        response.setMessage("У вас есть 5 минут чтобы зайти на сервер");
        response.setTimeout(30);
        return response;
    }

    public static RegistrationResponse successApproved() {
        RegistrationResponse response = new RegistrationResponse();
        response.setStatus("approved");
        response.setMessage("Регистрация успешно завершена");
        return response;
    }

    public static RegistrationResponse error(String error, String message) {
        RegistrationResponse response = new RegistrationResponse();
        response.setStatus("error");
        response.setError(error);
        response.setMessage(message);
        return response;
    }

    public static RegistrationResponse errorWithConflict(String error, String message, String conflict) {
        RegistrationResponse response = new RegistrationResponse();
        response.setStatus("error");
        response.setError(error);
        response.setMessage(message);
        response.setConflict(conflict);
        return response;
    }

    public static RegistrationResponse stillPending(long timeLeftSeconds) {
        RegistrationResponse response = new RegistrationResponse();
        response.setStatus("still_pending");
        response.setTimeLeftSeconds(timeLeftSeconds);
        return response;
    }

    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Integer getTimeout() { return timeout; }
    public Long getTimeLeftSeconds() { return timeLeftSeconds; }
    public String getError() { return error; }
    public String getConflict() { return conflict; }

    public void setStatus(String status) { this.status = status; }
    public void setMessage(String message) { this.message = message; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public void setTimeLeftSeconds(Long timeLeftSeconds) { this.timeLeftSeconds = timeLeftSeconds; }
    public void setError(String error) { this.error = error; }
    public void setConflict(String conflict) { this.conflict = conflict; }
}
