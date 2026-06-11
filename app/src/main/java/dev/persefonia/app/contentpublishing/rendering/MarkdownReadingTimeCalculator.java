package dev.persefonia.app.contentpublishing.rendering;

import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.ReadingTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownReadingTimeCalculator {
    private static final int WORDS_PER_MINUTE = 200;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    ReadingTime calculate(MarkdownSource source) {
        Matcher matcher = WORD.matcher(source.value());
        int words = 0;
        while (matcher.find()) {
            words++;
        }
        int minutes = Math.max(1, (words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE);
        return ReadingTime.minutes(minutes);
    }
}
