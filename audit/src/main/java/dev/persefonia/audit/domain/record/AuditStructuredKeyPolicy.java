package dev.persefonia.audit.domain.record;

import java.util.Set;
import java.util.regex.Pattern;

/** Grammar and exact sensitive-segment exclusions for Audit child keys. */
final class AuditStructuredKeyPolicy {
    private static final Pattern STRUCTURED_KEY =
            Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$");

    private static final Set<String> SENSITIVE_SEGMENTS = Set.of(
            "password",
            "passwd",
            "secret",
            "secrets",
            "client_secret",
            "api_secret",
            "api_key",
            "private_key",
            "token",
            "access_token",
            "refresh_token",
            "id_token",
            "session",
            "session_id",
            "cookie",
            "authorization",
            "credential",
            "credentials",
            "claims",
            "oidc_claims",
            "principal_payload",
            "request_payload",
            "response_payload",
            "request_headers",
            "headers",
            "request_uri",
            "raw_request_uri",
            "query_string",
            "raw_ip",
            "hashed_ip",
            "ip_address",
            "user_agent",
            "user_agent_summary",
            "fingerprint",
            "rate_limit_key",
            "redis_abuse_key",
            "body",
            "contact_body",
            "message_body",
            "markdown_source",
            "rendered_html",
            "html_body",
            "sender_email",
            "sender_name",
            "email",
            "email_address",
            "smtp_secret",
            "smtp_credential",
            "cloudflare_secret",
            "cloudflare_credential",
            "private_config",
            "private_runtime_config",
            "exception_message",
            "stack_trace");

    private AuditStructuredKeyPolicy() {
    }

    static String fieldPath(String value) {
        return validate(value, "field path");
    }

    static String metadataKey(String value) {
        return validate(value, "metadata key");
    }

    private static String validate(String value, String field) {
        String checked = AuditTextRules.requiredIdentifierText(value, field);
        if (!STRUCTURED_KEY.matcher(checked).matches()) {
            throw new AuditValidationException(
                    field + " must be a dotted lower-case snake-case structured key");
        }
        for (String segment : checked.split("\\.")) {
            if (SENSITIVE_SEGMENTS.contains(segment)) {
                throw new AuditValidationException(field + " uses a sensitive audit key");
            }
        }
        return checked;
    }
}
