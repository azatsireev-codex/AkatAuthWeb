package net.akat.auth.service.validator;

import net.akat.auth.dto.RegistrationRequest;
import net.akat.auth.repository.PlayerRepository;

public interface RegistrationValidator {
    ValidationResult validate(RegistrationRequest request, PlayerRepository repository);

    class ValidationResult {
        private final boolean valid;
        private final String errorCode;
        private final String errorMessage;
        private final String conflict;

        private ValidationResult(boolean valid, String errorCode, String errorMessage, String conflict) {
            this.valid = valid;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.conflict = conflict;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null, null, null);
        }

        public static ValidationResult invalid(String errorCode, String errorMessage) {
            return new ValidationResult(false, errorCode, errorMessage, null);
        }

        public static ValidationResult invalidWithConflict(String errorCode, String errorMessage, String conflict) {
            return new ValidationResult(false, errorCode, errorMessage, conflict);
        }

        // Getters
        public boolean isValid() { return valid; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public String getConflict() { return conflict; }
    }
}
