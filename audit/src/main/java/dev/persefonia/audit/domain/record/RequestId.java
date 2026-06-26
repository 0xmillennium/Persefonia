package dev.persefonia.audit.domain.record;

import java.util.regex.Pattern;

public record RequestId(String value) {
    public static final int MAX_LENGTH = 100;
    private static final Pattern CORRELATION_TOKEN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]*$");

    public RequestId {
        if (value == null) {
            throw new AuditValidationException("request id must not be null");
        }
        if (value.isBlank()) {
            throw new AuditValidationException("request id must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new AuditValidationException("request id must be at most " + MAX_LENGTH + " characters");
        }
        if (!CORRELATION_TOKEN.matcher(value).matches()) {
            throw new AuditValidationException("request id must be a safe correlation identifier");
        }
    }

    public static RequestId of(String value) {
        return new RequestId(value);
    }
}
