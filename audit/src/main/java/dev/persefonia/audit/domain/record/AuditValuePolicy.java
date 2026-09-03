package dev.persefonia.audit.domain.record;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Defense-in-depth checks for recognizable unsafe persisted-value shapes. This
 * is deliberately not a complete secret detector or arbitrary-content
 * classifier; source-specific allowlisted mapping remains the stronger boundary.
 */
final class AuditValuePolicy {
    private static final Pattern HTML_PAYLOAD = Pattern.compile(
            "<\\s*/?\\s*(?:p|div|script|html|body|span|section|article|h[1-6]|a)\\b[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+\\S");
    private static final Pattern MARKDOWN_BOLD = Pattern.compile("\\*\\*[^*]+\\*\\*");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^]]+]\\([^\\s)]+\\)");
    private static final Pattern EMAIL_ADDRESS =
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern ABSOLUTE_HTTP_URL =
            Pattern.compile("\\bhttps?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCEPTION_PAYLOAD = Pattern.compile(
            "\\b(?:[a-z_$][\\w$]*\\.)*[A-Z][\\w$]*(?:Exception|Error):\\s+\\S");
    private static final Pattern STACK_FRAME =
            Pattern.compile("^\\s*at\\s+[a-zA-Z_$][\\w$.]*(?:\\(|\\.\\.\\.)");
    private static final Pattern SOURCE_LOCATION = Pattern.compile("\\b[A-Za-z_$][\\w$]*\\.java:\\d+\\b");
    private static final Pattern CREDENTIAL_ASSIGNMENT = Pattern.compile(
            "(?i)(?:^|\\s)(?:password|passwd|secret|token|access_token|refresh_token|id_token|session|session_id|api_key|api_secret|client_secret|private_key)\\s*[:=]\\s*\\S+");
    private static final Pattern AUTHORIZATION_HEADER = Pattern.compile(
            "(?i)^\\s*authorization\\s*:\\s*(?:bearer|basic)\\s+\\S+");
    private static final Pattern AUTHORIZATION_VALUE =
            Pattern.compile("(?i)^\\s*(?:bearer|basic)\\s+\\S+");
    private static final Pattern COOKIE_HEADER = Pattern.compile("(?i)^\\s*cookie\\s*:\\s*\\S+");
    private static final Pattern JWT = Pattern.compile(
            "(?:^|\\s)[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}(?:$|\\s)");
    private static final Pattern PRIVATE_KEY_MARKER =
            Pattern.compile("-----BEGIN(?: [A-Z0-9]+)* PRIVATE KEY-----", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGEST = Pattern.compile("(?i)^(?:[0-9a-f]{32}|[0-9a-f]{40}|[0-9a-f]{64})$");
    private static final Pattern IPV4_CANDIDATE =
            Pattern.compile("(?<![0-9.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9.])");
    private static final Pattern INTERNAL_HOSTNAME =
            Pattern.compile("(?i)^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*\\.(?:internal|local)$");

    private AuditValuePolicy() {
    }

    static String auditValue(String value) {
        return validate(value, "audit value");
    }

    static String metadataValue(String value) {
        return validate(value, "metadata value");
    }

    private static String validate(String value, String field) {
        String checked = AuditTextRules.requiredSingleLine(value, field, AuditTextRules.MAX_VALUE_LENGTH);
        rejectAuthoredContent(checked, field);
        rejectIdentity(checked, field);
        rejectNetworkLocation(checked, field);
        rejectRequestTarget(checked, field);
        rejectFailureInternals(checked, field);
        rejectCredentials(checked, field);
        rejectStructuredSecrets(checked, field);
        return checked;
    }

    private static void rejectAuthoredContent(String value, String field) {
        if (HTML_PAYLOAD.matcher(value).find()
                || MARKDOWN_HEADING.matcher(value).find()
                || MARKDOWN_BOLD.matcher(value).find()
                || MARKDOWN_LINK.matcher(value).find()) {
            throw new AuditValidationException(field + " must not contain authored content");
        }
    }

    private static void rejectIdentity(String value, String field) {
        if (EMAIL_ADDRESS.matcher(value).find()) {
            throw new AuditValidationException(field + " must not contain email-like identity data");
        }
    }

    private static void rejectNetworkLocation(String value, String field) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        String host = networkHost(normalized);
        if (host.equals("localhost")
                || INTERNAL_HOSTNAME.matcher(host).matches()
                || containsIpv4Literal(normalized)
                || isIpv6Literal(host)) {
            throw new AuditValidationException(field + " must not contain raw network identity data");
        }
    }

    private static String networkHost(String value) {
        if (value.startsWith("[")) {
            int closingBracket = value.indexOf(']');
            if (closingBracket > 1
                    && (closingBracket == value.length() - 1
                    || isPortSuffix(value.substring(closingBracket + 1)))) {
                return value.substring(1, closingBracket);
            }
        }

        int lastColon = value.lastIndexOf(':');
        if (lastColon > 0
                && value.indexOf(':') == lastColon
                && isPortSuffix(value.substring(lastColon))) {
            return value.substring(0, lastColon);
        }
        return value;
    }

    private static boolean isPortSuffix(String value) {
        if (value.length() < 2 || value.charAt(0) != ':') {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsIpv4Literal(String value) {
        Matcher matcher = IPV4_CANDIDATE.matcher(value);
        while (matcher.find()) {
            String[] octets = matcher.group().split("\\.");
            boolean valid = true;
            for (String octet : octets) {
                if (Integer.parseInt(octet) > 255) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIpv6Literal(String value) {
        String candidate = value;
        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        if (!candidate.contains(":")
                || !candidate.matches("[0-9a-f:]+")
                || candidate.contains(":::")) {
            return false;
        }
        boolean compressed = candidate.contains("::");
        if (compressed && candidate.indexOf("::") != candidate.lastIndexOf("::")) {
            return false;
        }
        String[] parts = candidate.split(":", -1);
        int populatedParts = 0;
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (part.length() > 4) {
                    return false;
                }
                populatedParts++;
            }
        }
        if (compressed) {
            return populatedParts < 8;
        }
        return parts.length == 8 && populatedParts == 8;
    }

    private static void rejectRequestTarget(String value, String field) {
        if (ABSOLUTE_HTTP_URL.matcher(value).find() || isQueryBearingRelativeTarget(value)) {
            throw new AuditValidationException(field + " must not contain unsafe request data");
        }
    }

    private static boolean isQueryBearingRelativeTarget(String value) {
        int queryDelimiter = value.indexOf('?');
        if (queryDelimiter < 0) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return false;
            }
        }
        return !value.contains("://");
    }

    private static void rejectFailureInternals(String value, String field) {
        if (EXCEPTION_PAYLOAD.matcher(value).find()
                || STACK_FRAME.matcher(value).find()
                || SOURCE_LOCATION.matcher(value).find()) {
            throw new AuditValidationException(field + " must not contain exception or stack-trace data");
        }
    }

    private static void rejectCredentials(String value, String field) {
        if (CREDENTIAL_ASSIGNMENT.matcher(value).find()) {
            throw new AuditValidationException(field + " must not contain credential data");
        }
        if (AUTHORIZATION_HEADER.matcher(value).find()
                || AUTHORIZATION_VALUE.matcher(value).find()
                || COOKIE_HEADER.matcher(value).find()) {
            throw new AuditValidationException(field + " must not contain authorization or session data");
        }
    }

    private static void rejectStructuredSecrets(String value, String field) {
        String normalized = value.trim();
        if (JWT.matcher(normalized).find()
                || PRIVATE_KEY_MARKER.matcher(normalized).find()
                || DIGEST.matcher(normalized).matches()) {
            throw new AuditValidationException(field + " must not contain structured secret data");
        }
    }
}
