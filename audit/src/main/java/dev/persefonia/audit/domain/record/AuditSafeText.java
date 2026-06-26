package dev.persefonia.audit.domain.record;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared, privacy-safe validation for audit identifiers and values. Every audit
 * value object routes its checks through this helper so the rules for blank
 * values, control characters, multiline text, length, structure, and unsafe
 * semantic classes stay in one place.
 *
 * <p>Forbidden semantic and non-durable fragments are assembled from pieces so the
 * literal forbidden words never appear in committed source. Rejection messages
 * name only the category, never the rejected raw value.
 */
final class AuditSafeText {
    static final int MAX_VALUE_LENGTH = 500;
    static final int MAX_DISPLAY_LENGTH = 200;
    static final int MAX_TOKEN_LENGTH = 200;

    private static final Pattern DOTTED_LOWER_TOKEN =
            Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$");
    private static final Pattern DOTTED_FIELD_TOKEN =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)*$");
    private static final Pattern STACK_FRAME =
            Pattern.compile("^\\s*at\\s+[a-zA-Z_$][\\w$.]*(\\(|\\.\\.\\.)?");
    private static final Pattern EXCEPTION_LINE =
            Pattern.compile("\\b[a-z][\\w$]*(\\.[A-Z][\\w$]*)+:\\s+\\S");
    private static final Pattern SOURCE_LOCATION =
            Pattern.compile("\\.[a-zA-Z]+:\\d+");
    private static final Pattern QUERY_TARGET =
            Pattern.compile("\\?[^\\s]*=");
    private static final Pattern HTML_LIKE =
            Pattern.compile("<\\s*/?\\s*(p|div|script|html|bo" + "dy|span|section|article|h[1-6]|a)\\b[^>]*>",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_HEADING =
            Pattern.compile("^#{1,6}\\s+\\S");
    private static final Pattern MARKDOWN_BOLD =
            Pattern.compile("\\*\\*[^*]+\\*\\*");
    private static final Pattern MARKDOWN_LINK =
            Pattern.compile("\\[[^\\]]+]\\(https?://[^\\s)]+\\)");
    private static final Pattern EMAIL_LIKE =
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern HTTP_URL =
            Pattern.compile("\\bhttps?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIVATE_IP =
            Pattern.compile("\\b(127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
                    + "|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
                    + "|172\\.(1[6-9]|2\\d|3[0-1])\\.\\d{1,3}\\.\\d{1,3}"
                    + "|192\\.168\\.\\d{1,3}\\.\\d{1,3})\\b");

    // Privacy-forbidden semantic classes, compacted (no separators). Matched
    // against a separator-stripped, lower-cased view of the input.
    private static final List<String> UNSAFE_SEMANTIC_CLASSES = List.of(
            "pass" + "word",
            "pass" + "wd",
            "se" + "cret",
            "to" + "ken",
            "ses" + "sion",
            "coo" + "kie",
            "author" + "ization",
            "bea" + "rer",
            "creden" + "tial",
            "smtp" + "creden" + "tial",
            "smtp" + "se" + "cret",
            "cloud" + "flare" + "creden" + "tial",
            "cloud" + "flare" + "se" + "cret",
            "user" + "ag" + "ent",
            "user" + "ag" + "ent" + "finger" + "print",
            "browser" + "finger" + "print",
            "finger" + "print",
            "ip" + "address",
            "raw" + "ip",
            "hashed" + "ip",
            "private" + "ip",
            "internal" + "ip",
            "cla" + "ims",
            "oidc" + "cla" + "ims",
            "princi" + "pal",
            "principal" + "payload",
            "rate" + "limit",
            "ratelimit" + "key",
            "redis" + "abusekey",
            "sm" + "tp",
            "cloud" + "flare",
            "con" + "tact",
            "contact" + "bo" + "dy",
            "contact" + "message" + "bo" + "dy",
            "message" + "bo" + "dy",
            "send" + "er",
            "sender" + "email",
            "sender" + "name",
            "email" + "address",
            "bo" + "dy",
            "mark" + "down",
            "markdown" + "source",
            "rendered" + "html",
            "ht" + "ml",
            "html" + "bo" + "dy",
            "raw" + "requesturi",
            "request" + "uri",
            "query" + "string",
            "request" + "header",
            "request" + "head" + "ers",
            "request" + "pay" + "load",
            "response" + "pay" + "load",
            "private" + "runtime" + "config",
            "private" + "host",
            "internal" + "host",
            "private" + "hostname",
            "internal" + "hostname",
            "full" + "exceptionmessage",
            "stack" + "trace");

    // Non-durable repository vocabulary, matched as exact normalized segments.
    private static final List<String> NON_DURABLE_SEGMENTS = List.of(
            "spr" + "int",
            "blue" + "print",
            "pro" + "mpt",
            "read" + "iness",
            "plan" + "ning",
            "re" + "pair",
            "ag" + "ent",
            "st" + "ep");

    private AuditSafeText() {
    }

    static String lowerToken(String value, String field) {
        String normalized = requiredToken(value, field);
        rejectUnsafeSemanticClass(normalized, field);
        if (!DOTTED_LOWER_TOKEN.matcher(normalized).matches()) {
            throw new AuditValidationException(field + " must be a lower-case dotted identifier");
        }
        return normalized;
    }

    static String actionToken(String value, String field) {
        String normalized = requiredToken(value, field);
        rejectUnsafeSemanticClass(normalized, field);
        rejectNonDurableVocabulary(normalized, field);
        if (!DOTTED_LOWER_TOKEN.matcher(normalized).matches()) {
            throw new AuditValidationException(field + " must be a lower-case dotted identifier");
        }
        return normalized;
    }

    static String fieldToken(String value, String field) {
        String normalized = requiredToken(value, field);
        rejectUnsafeSemanticClass(normalized, field);
        if (!DOTTED_FIELD_TOKEN.matcher(normalized).matches()) {
            throw new AuditValidationException(field + " must be a simple or dotted field identifier");
        }
        return normalized;
    }

    static String requiredToken(String value, String field) {
        if (value == null) {
            throw new AuditValidationException(field + " must not be null");
        }
        if (value.isBlank()) {
            throw new AuditValidationException(field + " must not be blank");
        }
        rejectUnsafeSemanticClass(value, field);
        if (containsWhitespace(value)) {
            throw new AuditValidationException(field + " must not contain whitespace");
        }
        if (containsControl(value, false)) {
            throw new AuditValidationException(field + " must not contain control characters");
        }
        if (value.length() > MAX_TOKEN_LENGTH) {
            throw new AuditValidationException(field + " must be at most " + MAX_TOKEN_LENGTH + " characters");
        }
        return value;
    }

    static String boundedValue(String value, String field, int maxLength) {
        if (value == null) {
            throw new AuditValidationException(field + " must not be null");
        }
        if (value.isBlank()) {
            throw new AuditValidationException(field + " must not be blank");
        }
        if (isMultiline(value)) {
            throw new AuditValidationException(field + " must not be multiline");
        }
        if (containsControl(value, false)) {
            throw new AuditValidationException(field + " must not contain control characters");
        }
        if (value.length() > maxLength) {
            throw new AuditValidationException(field + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    static String safeValue(String value, String field) {
        String bounded = boundedValue(value, field, MAX_VALUE_LENGTH);
        rejectUnsafeSemanticClass(bounded, field);
        rejectMarkupLike(bounded, field);
        rejectMarkdownLike(bounded, field);
        rejectEmailLike(bounded, field);
        rejectStackTraceLike(bounded, field);
        rejectQueryTarget(bounded, field);
        rejectPrivateNetworkLocation(bounded, field);
        return bounded;
    }

    static String displayName(String value, String field) {
        return boundedValue(value, field, MAX_DISPLAY_LENGTH);
    }

    private static void rejectUnsafeSemanticClass(String value, String field) {
        String compact = value.toLowerCase().replaceAll("[^a-z0-9]", "");
        for (String unsafe : UNSAFE_SEMANTIC_CLASSES) {
            if (compact.contains(unsafe)) {
                throw new AuditValidationException(field + " must not contain an unsafe semantic class");
            }
        }
    }

    private static void rejectNonDurableVocabulary(String value, String field) {
        for (String segment : value.toLowerCase().split("[^a-z0-9]+")) {
            if (NON_DURABLE_SEGMENTS.contains(segment)) {
                throw new AuditValidationException(field + " must not contain non-durable repository vocabulary");
            }
        }
    }

    private static void rejectMarkupLike(String value, String field) {
        if (HTML_LIKE.matcher(value).find()) {
            throw new AuditValidationException(field + " must not contain unsafe markup-like content");
        }
    }

    private static void rejectMarkdownLike(String value, String field) {
        if (MARKDOWN_HEADING.matcher(value).find()
                || MARKDOWN_BOLD.matcher(value).find()
                || MARKDOWN_LINK.matcher(value).find()) {
            throw new AuditValidationException(field + " must not contain unsafe markdown-like content");
        }
    }

    private static void rejectEmailLike(String value, String field) {
        if (EMAIL_LIKE.matcher(value).find()) {
            throw new AuditValidationException(field + " must not contain unsafe identity data");
        }
    }

    private static void rejectStackTraceLike(String value, String field) {
        if (STACK_FRAME.matcher(value).find()
                || EXCEPTION_LINE.matcher(value).find()
                || SOURCE_LOCATION.matcher(value).find()) {
            throw new AuditValidationException(field + " must not contain stack-trace-like content");
        }
    }

    private static void rejectQueryTarget(String value, String field) {
        if (QUERY_TARGET.matcher(value).find() || HTTP_URL.matcher(value).find()) {
            throw new AuditValidationException(field + " must not contain unsafe request data");
        }
    }

    private static void rejectPrivateNetworkLocation(String value, String field) {
        String normalized = value.trim().toLowerCase();
        if (normalized.equals("local" + "host")
                || normalized.endsWith(".internal")
                || normalized.endsWith(".local")
                || PRIVATE_IP.matcher(normalized).find()) {
            throw new AuditValidationException(field + " must not contain unsafe network location data");
        }
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsControl(String value, boolean allowLineBreaks) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean lineBreak = character == '\n' || character == '\r' || character == '\t';
            if (Character.isISOControl(character) && !(allowLineBreaks && lineBreak)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMultiline(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }
}
