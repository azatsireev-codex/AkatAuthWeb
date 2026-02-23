package net.akat.auth.service.validator;

import net.akat.auth.dto.RegistrationRequest;
import net.akat.auth.repository.PlayerRepository;

public class IpValidator implements RegistrationValidator {
    @Override
    public ValidationResult validate(RegistrationRequest request, PlayerRepository repository) {
        String ip = request.getIpAddress();

        if (ip == null || ip.isEmpty()) {
            return ValidationResult.invalid("MISSING_IP", "IP адрес обязателен");
        }

        String conflictingPlayer = repository.findByIp(ip);
        if (conflictingPlayer != null) {
            return ValidationResult.invalidWithConflict(
                    "IP_IN_USE",
                    "Этот IP-адрес уже используется другим аккаунтом",
                    conflictingPlayer
            );
        }

        return ValidationResult.valid();
    }
}
