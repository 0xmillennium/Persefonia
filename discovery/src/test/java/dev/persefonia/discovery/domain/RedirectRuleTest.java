package dev.persefonia.discovery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedirectRuleTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-14T08:00:00Z");

    @Test
    void createsManualRedirectWithOptionalSourceReference() {
        RedirectRule withoutSource = RedirectRule.createManual(
                RedirectRuleId.random(), url("/old"), url("/new"), RedirectStatusCode.FOUND_302, null,
                CREATED_AT, Version.initial());
        RedirectRule withSource = RedirectRule.createManual(
                RedirectRuleId.random(), url("/older"), url("/newer"), RedirectStatusCode.PERMANENT_REDIRECT_308,
                sourceRef(), CREATED_AT, Version.initial());

        assertThat(withoutSource.reason()).isEqualTo(RedirectReason.MANUAL);
        assertThat(withoutSource.sourceRef()).isEmpty();
        assertThat(withSource.sourceRef()).contains(sourceRef());
    }

    @Test
    void createsSlugChangedRedirectWith301() {
        RedirectRule rule = RedirectRule.createSlugChanged(
                RedirectRuleId.random(), url("/old"), url("/new"), sourceRef(), CREATED_AT, Version.initial());

        assertThat(rule.statusCode()).isEqualTo(RedirectStatusCode.MOVED_PERMANENTLY_301);
        assertThat(rule.reason()).isEqualTo(RedirectReason.SLUG_CHANGED);
        assertThat(rule.active()).isTrue();
    }

    @Test
    void rejectsSelfRedirectAndSlugChangedWithout301() {
        PublicUrl sameUrl = url("/same");

        assertThatThrownBy(() -> RedirectRule.createManual(
                        RedirectRuleId.random(), sameUrl, sameUrl, RedirectStatusCode.FOUND_302, null,
                        CREATED_AT, Version.initial()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceUrl and targetUrl must differ");
        assertThatThrownBy(() -> RedirectRule.create(
                        RedirectRuleId.random(), url("/old"), url("/new"), RedirectStatusCode.PERMANENT_REDIRECT_308,
                        RedirectReason.SLUG_CHANGED, sourceRef(), true, CREATED_AT, CREATED_AT, Version.initial()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SLUG_CHANGED redirects must use 301");
    }

    @Test
    void deactivatesOnceAndIsThenIdempotent() {
        RedirectRule active = RedirectRule.createManual(
                RedirectRuleId.random(), url("/old"), url("/new"), RedirectStatusCode.FOUND_302, null,
                CREATED_AT, Version.initial());
        Instant deactivatedAt = CREATED_AT.plusSeconds(60);

        RedirectRule inactive = active.deactivate(deactivatedAt);

        assertThat(inactive.active()).isFalse();
        assertThat(inactive.updatedAt()).isEqualTo(deactivatedAt);
        assertThat(inactive.version()).isEqualTo(Version.initial().next());
        assertThat(inactive.deactivate(deactivatedAt.plusSeconds(60))).isSameAs(inactive);
    }

    @Test
    void acceptsRedirectChainMemberWithoutRepositoryDependentLoopDetection() {
        RedirectRule rule = RedirectRule.createManual(
                RedirectRuleId.random(), url("/a"), url("/b"), RedirectStatusCode.FOUND_302, null,
                CREATED_AT, Version.initial());

        assertThat(rule.sourceUrl()).isEqualTo(url("/a"));
        assertThat(rule.targetUrl()).isEqualTo(url("/b"));
    }

    private static PublicUrl url(String value) {
        return new PublicUrl(value);
    }

    private static SourceEntityRef sourceRef() {
        return new SourceEntityRef(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                new SourceEntityId(UUID.fromString("d4c57198-c3d4-477f-839b-7b48848628ec")));
    }
}
