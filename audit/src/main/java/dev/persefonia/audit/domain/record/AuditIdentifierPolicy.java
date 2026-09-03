package dev.persefonia.audit.domain.record;

import java.util.regex.Pattern;

/** Grammar for durable Audit source and action identifiers. */
final class AuditIdentifierPolicy {
    private static final Pattern LOWER_SNAKE_IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Pattern DOTTED_LOWER_SNAKE_IDENTIFIER =
            Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$");

    private AuditIdentifierPolicy() {
    }

    static String sourceContext(String value) {
        return singleIdentifier(value, "source context");
    }

    static String sourceType(String value) {
        return singleIdentifier(value, "source type");
    }

    static String action(String value) {
        String checked = AuditTextRules.requiredIdentifierText(value, "audit action");
        if (!DOTTED_LOWER_SNAKE_IDENTIFIER.matcher(checked).matches()) {
            throw new AuditValidationException(
                    "audit action must be a dotted lower-case snake-case identifier");
        }
        return checked;
    }

    private static String singleIdentifier(String value, String field) {
        String checked = AuditTextRules.requiredIdentifierText(value, field);
        if (!LOWER_SNAKE_IDENTIFIER.matcher(checked).matches()) {
            throw new AuditValidationException(field + " must be a lower-case snake-case identifier");
        }
        return checked;
    }
}
