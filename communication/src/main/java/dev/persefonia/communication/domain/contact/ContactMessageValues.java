package dev.persefonia.communication.domain.contact;

import java.util.Objects;

final class ContactMessageValues {
    private ContactMessageValues() {
    }

    static String boundedText(String value, String field, int maxLength, boolean multiline) {
        Objects.requireNonNull(value, field + " must not be null");
        if (containsUnsafeControl(value, multiline)) {
            throw new ContactMessageValidationException(field + " contains unsupported control characters");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new ContactMessageValidationException(field + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new ContactMessageValidationException(field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private static boolean containsUnsafeControl(String value, boolean multiline) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) && !(multiline && (character == '\n' || character == '\r' || character == '\t'))) {
                return true;
            }
        }
        return false;
    }
}
