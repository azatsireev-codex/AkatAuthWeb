package net.akat.auth.service.validator;

import net.akat.auth.dto.RegistrationRequest;
import net.akat.auth.repository.PlayerRepository;

public class EmailValidator implements RegistrationValidator {
    @Override
    public ValidationResult validate(RegistrationRequest request, PlayerRepository repository) {
        String email = request.getEmail();

        if (email == null || email.isEmpty()) {
            return ValidationResult.invalid("MISSING_EMAIL", "Email обязателен");
        }

        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return ValidationResult.invalid("INVALID_EMAIL", "Некорректный формат email");
        }

        String conflictingPlayer = repository.findByEmail(email);
        if (conflictingPlayer != null) {
            return ValidationResult.invalid("EMAIL_IN_USE", "Этот email уже зарегистрирован");
        }

        return ValidationResult.valid();
    }
}
