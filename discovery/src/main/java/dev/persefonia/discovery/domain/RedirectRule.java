package dev.persefonia.discovery.domain;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class RedirectRule {
    private final RedirectRuleId id;
    private final PublicUrl sourceUrl;
    private final PublicUrl targetUrl;
    private final RedirectStatusCode statusCode;
    private final RedirectReason reason;
    private final SourceEntityRef sourceRef;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Version version;

    private RedirectRule(
            RedirectRuleId id,
            PublicUrl sourceUrl,
            PublicUrl targetUrl,
            RedirectStatusCode statusCode,
            RedirectReason reason,
            SourceEntityRef sourceRef,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceUrl = Objects.requireNonNull(sourceUrl, "sourceUrl");
        this.targetUrl = Objects.requireNonNull(targetUrl, "targetUrl");
        this.statusCode = Objects.requireNonNull(statusCode, "statusCode");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.sourceRef = sourceRef;
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = Objects.requireNonNull(version, "version");

        if (sourceUrl.equals(targetUrl)) {
            throw new IllegalArgumentException("sourceUrl and targetUrl must differ");
        }
        if (reason == RedirectReason.SLUG_CHANGED
                && statusCode != RedirectStatusCode.MOVED_PERMANENTLY_301) {
            throw new IllegalArgumentException("SLUG_CHANGED redirects must use 301");
        }
    }

    public static RedirectRule create(
            RedirectRuleId id,
            PublicUrl sourceUrl,
            PublicUrl targetUrl,
            RedirectStatusCode statusCode,
            RedirectReason reason,
            SourceEntityRef sourceRef,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        return new RedirectRule(
                id, sourceUrl, targetUrl, statusCode, reason, sourceRef, active, createdAt, updatedAt, version);
    }

    public static RedirectRule createSlugChanged(
            RedirectRuleId id,
            PublicUrl sourceUrl,
            PublicUrl targetUrl,
            SourceEntityRef sourceRef,
            Instant createdAt,
            Version version) {
        return create(
                id, sourceUrl, targetUrl, RedirectStatusCode.MOVED_PERMANENTLY_301, RedirectReason.SLUG_CHANGED,
                sourceRef, true, createdAt, createdAt, version);
    }

    public static RedirectRule createManual(
            RedirectRuleId id,
            PublicUrl sourceUrl,
            PublicUrl targetUrl,
            RedirectStatusCode statusCode,
            SourceEntityRef sourceRef,
            Instant createdAt,
            Version version) {
        return create(
                id, sourceUrl, targetUrl, statusCode, RedirectReason.MANUAL, sourceRef, true, createdAt, createdAt, version);
    }

    public RedirectRule deactivate(Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!active) {
            return this;
        }
        return new RedirectRule(
                id, sourceUrl, targetUrl, statusCode, reason, sourceRef, false, createdAt, updatedAt, version.next());
    }

    public RedirectRuleId id() {
        return id;
    }

    public PublicUrl sourceUrl() {
        return sourceUrl;
    }

    public PublicUrl targetUrl() {
        return targetUrl;
    }

    public RedirectStatusCode statusCode() {
        return statusCode;
    }

    public RedirectReason reason() {
        return reason;
    }

    public Optional<SourceEntityRef> sourceRef() {
        return Optional.ofNullable(sourceRef);
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Version version() {
        return version;
    }
}
