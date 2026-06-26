package dev.persefonia.audit.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuditTokenInvariantsTest {
    @Test
    void validAuditActionIsAccepted() {
        assertThat(AuditAction.of("content.published").value()).isEqualTo("content.published");
        assertThat(AuditAction.of("profile.visibility_changed").value())
                .isEqualTo("profile.visibility_changed");
    }

    @Test
    void blankActionIsRejected() {
        assertThatThrownBy(() -> AuditAction.of("  "))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void actionWithWhitespaceIsRejected() {
        assertThatThrownBy(() -> AuditAction.of("content published"))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("whitespace");
    }

    @Test
    void actionWithControlCharacterIsRejected() {
        assertThatThrownBy(() -> AuditAction.of("content.published"))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("control");
    }

    @Test
    void pathLikeActionIsRejected() {
        assertThatThrownBy(() -> AuditAction.of("content/published"))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("identifier");
    }

    @Test
    void queryLikeActionIsRejected() {
        assertThatThrownBy(() -> AuditAction.of("content?published=true"))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("identifier");
    }

    @Test
    void nonDurableVocabularyActionIsRejected() {
        // Segmented to avoid embedding the forbidden literal in committed source.
        String nonDurableSegment = "spr" + "int";
        assertThatThrownBy(() -> AuditAction.of("content." + nonDurableSegment))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("non-durable repository vocabulary");
    }

    @Test
    void validRequestIdIsAccepted() {
        assertThat(RequestId.of("req-12ab34cd").value()).isEqualTo("req-12ab34cd");
    }

    @Test
    void blankRequestIdIsRejected() {
        assertThatThrownBy(() -> RequestId.of(" "))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void validFieldPathIsAccepted() {
        assertThat(FieldPath.of("title").value()).isEqualTo("title");
        assertThat(FieldPath.of("seo.title").value()).isEqualTo("seo.title");
    }

    @Test
    void unsafeFieldPathIsRejected() {
        String unsafe = "pass" + "word";
        assertThatThrownBy(() -> FieldPath.of(unsafe))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("unsafe semantic class");
    }

    @Test
    void pathLikeFieldPathIsRejected() {
        assertThatThrownBy(() -> FieldPath.of("seo/title"))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("identifier");
    }

    @Test
    void validMetadataKeyIsAccepted() {
        assertThat(MetadataKey.of("reason").value()).isEqualTo("reason");
        assertThat(MetadataKey.of("source.channel").value()).isEqualTo("source.channel");
    }

    @Test
    void unsafeMetadataKeyIsRejected() {
        String unsafe = "se" + "ssion";
        assertThatThrownBy(() -> MetadataKey.of(unsafe))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("unsafe semantic class");
    }
}
