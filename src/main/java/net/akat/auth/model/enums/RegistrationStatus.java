package net.akat.auth.model.enums;

public enum RegistrationStatus {
    PENDING("pending", "Регистрация ожидает подтверждения"),
    APPROVED("approved", "Регистрация завершена успешно"),
    EXPIRED("expired", "Время регистрации истекло"),
    IP_MISMATCH("ip_mismatch", "IP-адрес не совпадает"),
    ERROR("error", "Ошибка регистрации");

    private final String code;
    private final String description;

    RegistrationStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }

    public static RegistrationStatus fromCode(String code) {
        for (RegistrationStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return ERROR;
    }
}
