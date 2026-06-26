package dev.persefonia.app.contentpublishing.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.domain.content.HeadingAnchor;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeadingAnchorGeneratorTest {
    private final HeadingAnchorGenerator generator = new HeadingAnchorGenerator();

    @Test
    void normalizesAsciiTurkishDiacriticsAndPunctuation() {
        assertThat(values(generator.generateUniqueAnchors(List.of(
                        "ASCII Heading",
                        "Merhaba Dünya",
                        "İçerik Başlığı",
                        "Crème brûlée",
                        "C++ & Java",
                        "---Repeated--- punctuation!!!"))))
                .containsExactly(
                        "ascii-heading",
                        "merhaba-dunya",
                        "icerik-basligi",
                        "creme-brulee",
                        "c-java",
                        "repeated-punctuation");
    }

    @Test
    void fallsBackAndCreatesDeterministicDuplicateSuffixes() {
        assertThat(values(generator.generateUniqueAnchors(List.of("!!!", "!!!", "Intro", "Intro", "Intro"))))
                .containsExactly("section", "section-2", "intro", "intro-2", "intro-3");
    }

    @Test
    void generatesDomainValidAnchors() {
        assertThat(generator.generateUniqueAnchors(List.of("İçerik Başlığı", "C++ & Java")))
                .allSatisfy(anchor -> assertThat(HeadingAnchor.of(anchor.value())).isEqualTo(anchor));
    }

    private static List<String> values(List<HeadingAnchor> anchors) {
        return anchors.stream().map(anchor -> anchor.value()).toList();
    }
}
