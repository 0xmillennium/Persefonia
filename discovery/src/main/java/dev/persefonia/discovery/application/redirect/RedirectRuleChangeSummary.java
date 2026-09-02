package dev.persefonia.discovery.application.redirect;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.domain.RedirectRuleId;
import java.util.Objects;

public record RedirectRuleChangeSummary(
        RedirectRuleId redirectRuleId,
        PublicUrl sourceUrl,
        PublicUrl targetUrl,
        RedirectStatusCode statusCode,
        RedirectReason reason) {
    public RedirectRuleChangeSummary {
        Objects.requireNonNull(redirectRuleId, "redirectRuleId");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(targetUrl, "targetUrl");
        Objects.requireNonNull(statusCode, "statusCode");
        Objects.requireNonNull(reason, "reason");
    }
}
