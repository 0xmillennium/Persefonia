package dev.persefonia.discovery.application.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscoveryRedirectContractTest {
    @Test
    void redirectCommandAcceptsValidManualRedirectWithoutSourceReference() {
        CreateRedirectRuleCommand command = new CreateRedirectRuleCommand(
                new PublicUrl("/en/old"),
                new PublicUrl("/en/new"),
                RedirectStatusCode.FOUND_302,
                RedirectReason.MANUAL,
                null,
                null,
                null);

        assertThat(command.reason()).isEqualTo(RedirectReason.MANUAL);
    }

    @Test
    void redirectCommandAcceptsValidSlugChangedPermanentRedirect() {
        CreateRedirectRuleCommand command = new CreateRedirectRuleCommand(
                new PublicUrl("/en/articles/old"),
                new PublicUrl("/en/articles/new"),
                RedirectStatusCode.MOVED_PERMANENTLY_301,
                RedirectReason.SLUG_CHANGED,
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                sourceEntityId());

        assertThat(command.sourceEntityId()).isEqualTo(sourceEntityId());
    }

    @Test
    void redirectCommandRejectsSelfRedirect() {
        PublicUrl sameUrl = new PublicUrl("/en/articles/same");

        assertThatThrownBy(() -> new CreateRedirectRuleCommand(
                        sameUrl,
                        sameUrl,
                        RedirectStatusCode.MOVED_PERMANENTLY_301,
                        RedirectReason.MANUAL,
                        null,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceUrl and targetUrl must differ");
    }

    @Test
    void slugChangedRedirectRequires301() {
        assertThatThrownBy(() -> new CreateRedirectRuleCommand(
                        new PublicUrl("/en/articles/old"),
                        new PublicUrl("/en/articles/new"),
                        RedirectStatusCode.PERMANENT_REDIRECT_308,
                        RedirectReason.SLUG_CHANGED,
                        SourceContext.CONTENT_PUBLISHING,
                        SourceType.CONTENT_ITEM,
                        sourceEntityId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SLUG_CHANGED redirects must use 301");
    }

    @Test
    void redirectCommandRejectsPartiallyPresentSourceReference() {
        assertThatThrownBy(() -> new CreateRedirectRuleCommand(
                        new PublicUrl("/en/old"),
                        new PublicUrl("/en/new"),
                        RedirectStatusCode.FOUND_302,
                        RedirectReason.MANUAL,
                        SourceContext.CONTENT_PUBLISHING,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source reference must be fully present or fully absent");
    }

    @Test
    void redirectStatusCodeSupportsOnlyLockedStatusCodes() {
        assertThat(RedirectStatusCode.values())
                .extracting(RedirectStatusCode::value)
                .containsExactlyInAnyOrder(301, 302, 307, 308);
    }

    @Test
    void redirectResultExposesOnlySafeChangeSummary() {
        assertThat(RedirectRuleCreationResult.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(
                        RedirectRuleCreationResult.Created.class,
                        RedirectRuleCreationResult.Noop.class,
                        RedirectRuleCreationResult.Rejected.class);
        assertThat(RedirectRuleCreationResult.Created.class.getRecordComponents())
                .singleElement()
                .extracting(component -> component.getType())
                .isEqualTo(RedirectRuleChangeSummary.class);
        assertThat(RedirectRuleCreationResult.Noop.class.getRecordComponents())
                .singleElement()
                .extracting(component -> component.getType())
                .isEqualTo(RedirectRuleChangeSummary.class);
        assertThat(RedirectRuleCreationResult.Rejected.class.getRecordComponents())
                .singleElement()
                .extracting(component -> component.getType())
                .isEqualTo(RedirectRuleCreationResult.Reason.class);
    }

    @Test
    void deactivationResultsExposeSafeSummaryOrRequestedId() {
        assertThat(DeactivateRedirectRuleResult.Deactivated.class.getRecordComponents())
                .singleElement()
                .extracting(component -> component.getType())
                .isEqualTo(RedirectRuleChangeSummary.class);
        assertThat(DeactivateRedirectRuleResult.AlreadyInactive.class.getRecordComponents())
                .singleElement()
                .extracting(component -> component.getType())
                .isEqualTo(RedirectRuleChangeSummary.class);
        assertThat(DeactivateRedirectRuleResult.NotFound.class.getRecordComponents())
                .singleElement()
                .extracting(component -> component.getType())
                .isEqualTo(dev.persefonia.discovery.domain.RedirectRuleId.class);
    }

    @Test
    void redirectChangeSummaryContainsOnlySafeMutationFacts() {
        assertThat(RedirectRuleChangeSummary.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("redirectRuleId", "sourceUrl", "targetUrl", "statusCode", "reason");
    }

    private static SourceEntityId sourceEntityId() {
        return new SourceEntityId(UUID.fromString("d4c57198-c3d4-477f-839b-7b48848628ec"));
    }
}
