package net.akat.auth.service.validator;

import net.akat.auth.dto.RegistrationRequest;
import net.akat.auth.repository.PlayerRepository;

public class NicknameValidator implements RegistrationValidator {
    @Override
    public ValidationResult validate(RegistrationRequest request, PlayerRepository repository) {
        String nickname = request.getNickname();

        if (nickname == null || nickname.isEmpty()) {
            return ValidationResult.invalid("MISSING_NICKNAME", "Никнейм обязателен");
        }

        if (nickname.length() < 3 || nickname.length() > 16) {
            return ValidationResult.invalid("INVALID_NICKNAME_LENGTH", "Никнейм должен быть от 3 до 16 символов");
        }

        if (!nickname.matches("^[a-zA-Z0-9_]+$")) {
            return ValidationResult.invalid("INVALID_NICKNAME_FORMAT", "Никнейм может содержать только буквы, цифры и подчёркивания");
        }

        boolean exists = repository.existsByNickname(nickname);
        if (exists) {
            return ValidationResult.invalid("NICKNAME_TAKEN", "Этот никнейм уже занят");
        }

        return ValidationResult.valid();
    }
}
