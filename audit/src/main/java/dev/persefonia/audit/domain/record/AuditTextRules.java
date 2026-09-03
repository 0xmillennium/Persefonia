package dev.persefonia.audit.domain.record;

/** Shared mechanical constraints for text stored by the Audit domain. */
final class AuditTextRules {
    static final int MAX_VALUE_LENGTH = 500;
    static final int MAX_DISPLAY_LENGTH = 200;
    static final int MAX_IDENTIFIER_LENGTH = 200;

    private AuditTextRules() {
    }

    static String requiredSingleLine(String value, String field, int maxLength) {
        if (value == null) {
            throw new AuditValidationException(field + " must not be null");
        }
        if (value.isBlank()) {
            throw new AuditValidationException(field + " must not be blank");
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new AuditValidationException(field + " must not be multiline");
        }
        if (containsControlCharacter(value)) {
            throw new AuditValidationException(field + " must not contain control characters");
        }
        if (value.length() > maxLength) {
            throw new AuditValidationException(field + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    static String requiredIdentifierText(String value, String field) {
        String checked = requiredSingleLine(value, field, MAX_IDENTIFIER_LENGTH);
        if (containsWhitespace(checked)) {
            throw new AuditValidationException(field + " must not contain whitespace");
        }
        return checked;
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }
}
