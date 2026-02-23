package net.akat.auth.service.validator;

import java.util.Arrays;
import java.util.List;

public class ValidatorFactory {
    public static List<RegistrationValidator> createAllValidators() {
        return Arrays.asList(
                new NicknameValidator(),
                new EmailValidator(),
                new IpValidator()
        );
    }
}
