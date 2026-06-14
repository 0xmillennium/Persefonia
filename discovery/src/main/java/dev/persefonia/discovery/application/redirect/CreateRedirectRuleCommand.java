package dev.persefonia.discovery.application.redirect;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import java.util.Objects;

/**
 * Redirect creation request. The source reference is optional, but must be
 * either fully present or fully absent.
 */
public record CreateRedirectRuleCommand(
        PublicUrl sourceUrl,
        PublicUrl targetUrl,
        RedirectStatusCode statusCode,
        RedirectReason reason,
        SourceContext sourceContext,
        SourceType sourceType,
        SourceEntityId sourceEntityId) {
    public CreateRedirectRuleCommand {
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(targetUrl, "targetUrl");
        Objects.requireNonNull(statusCode, "statusCode");
        Objects.requireNonNull(reason, "reason");

        if (sourceUrl.equals(targetUrl)) {
            throw new IllegalArgumentException("sourceUrl and targetUrl must differ");
        }
        if (reason == RedirectReason.SLUG_CHANGED
                && statusCode != RedirectStatusCode.MOVED_PERMANENTLY_301) {
            throw new IllegalArgumentException("SLUG_CHANGED redirects must use 301");
        }

        int sourceReferenceParts = (sourceContext == null ? 0 : 1)
                + (sourceType == null ? 0 : 1)
                + (sourceEntityId == null ? 0 : 1);
        if (sourceReferenceParts != 0 && sourceReferenceParts != 3) {
            throw new IllegalArgumentException("source reference must be fully present or fully absent");
        }
    }
}
