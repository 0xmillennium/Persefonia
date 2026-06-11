package dev.persefonia.app.contentpublishing.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class MarkdownReadingTimeCalculatorTest {
    private final MarkdownReadingTimeCalculator calculator = new MarkdownReadingTimeCalculator();

    @Test
    void usesTwoHundredWordsPerMinuteWithCeilingAndMinimumOne() {
        assertThat(minutes("word")).isEqualTo(1);
        assertThat(minutes(words(200))).isEqualTo(1);
        assertThat(minutes(words(201))).isEqualTo(2);
        assertThat(minutes(words(400))).isEqualTo(2);
        assertThat(minutes(words(401))).isEqualTo(3);
        assertThat(minutes("... !!!")).isEqualTo(1);
    }

    @Test
    void countsTurkishLetterAndNumberTokensWithoutInflatingPunctuation() {
        assertThat(minutes("İçerik, başlığı; Türkçe! 2026...")).isEqualTo(1);
        assertThat(minutes(String.join(" -- ", Collections.nCopies(201, "İçerik")))).isEqualTo(2);
    }

    private int minutes(String source) {
        return calculator.calculate(MarkdownSource.of(source)).minutes();
    }

    private static String words(int count) {
        return String.join(" ", Collections.nCopies(count, "word"));
    }
}
