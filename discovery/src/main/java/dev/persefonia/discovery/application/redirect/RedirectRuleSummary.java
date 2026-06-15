package dev.persefonia.discovery.application.redirect;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.domain.RedirectRuleId;
import dev.persefonia.discovery.domain.Version;
import java.time.Instant;
import java.util.Objects;

public record RedirectRuleSummary(
        RedirectRuleId id,
        PublicUrl sourceUrl,
        PublicUrl targetUrl,
        RedirectStatusCode statusCode,
        RedirectReason reason,
        boolean active,
        SourceContext sourceContext,
        SourceType sourceType,
        SourceEntityId sourceEntityId,
        Instant createdAt,
        Instant updatedAt,
        Version version) {
    public RedirectRuleSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(targetUrl, "targetUrl");
        Objects.requireNonNull(statusCode, "statusCode");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(version, "version");

        int sourceReferenceParts = (sourceContext == null ? 0 : 1)
                + (sourceType == null ? 0 : 1)
                + (sourceEntityId == null ? 0 : 1);
        if (sourceReferenceParts != 0 && sourceReferenceParts != 3) {
            throw new IllegalArgumentException("source reference must be fully present or fully absent");
        }
    }

    public boolean hasSourceRef() {
        return sourceContext != null;
    }
}
