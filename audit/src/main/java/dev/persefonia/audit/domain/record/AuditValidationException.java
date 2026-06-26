package dev.persefonia.audit.domain.record;

/**
 * Raised when audit input violates a domain invariant or a privacy-safe value
 * rule. Messages name the rejected category only; they never echo the rejected
 * raw value, so unsafe content cannot leak through exception text.
 */
public class AuditValidationException extends RuntimeException {
    public AuditValidationException(String message) {
        super(message);
    }
}
