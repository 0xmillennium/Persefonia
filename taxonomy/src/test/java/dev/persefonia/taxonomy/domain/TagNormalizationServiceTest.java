package dev.persefonia.taxonomy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.taxonomy.domain.model.TagName;
import dev.persefonia.taxonomy.domain.model.TagValidationException;
import dev.persefonia.taxonomy.domain.service.TagNormalizationService;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TagNormalizationServiceTest {
    private final TagNormalizationService normalization = new TagNormalizationService();

    @Test
    void normalizesNameUsingRootLocaleAndCollapsesWhitespace() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(normalization.normalizeName(TagName.of("  JAVA   İÇERİK  ")).value())
                    .isEqualTo("java içerik");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void generatesStableAsciiSlugForTurkishCharacters() {
        assertThat(normalization.generateSlug(TagName.of("İçerik Şablonu")).value())
                .isEqualTo("icerik-sablonu");
        assertThat(normalization.normalizeSlug("  Java & Spring  ").value())
                .isEqualTo("java-spring");
    }

    @Test
    void rejectsInputThatCannotProduceSlug() {
        assertThatThrownBy(() -> normalization.normalizeSlug("---"))
                .isInstanceOf(TagValidationException.class);
    }
}
