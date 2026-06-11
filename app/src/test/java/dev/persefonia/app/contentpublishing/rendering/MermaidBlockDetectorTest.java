package dev.persefonia.app.contentpublishing.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import org.junit.jupiter.api.Test;

class MermaidBlockDetectorTest {
    private final CommonmarkMarkdownParser parser = new CommonmarkMarkdownParser();
    private final MermaidBlockDetector detector = new MermaidBlockDetector();

    @Test
    void detectsMermaidInfoTokenCaseInsensitivelyWithTrailingTokens() {
        assertThat(detect("```mermaid\ngraph TD\n```")).isTrue();
        assertThat(detect("```MERMAID\ngraph TD\n```")).isTrue();
        assertThat(detect("```mermaid title=\"flow\"\ninvalid mermaid syntax\n```")).isTrue();
    }

    @Test
    void ignoresNonMermaidBlocksAndParagraphMentions() {
        assertThat(detect("```not-mermaid\nmermaid\n```")).isFalse();
        assertThat(detect("This paragraph mentions mermaid.")).isFalse();
    }

    private boolean detect(String markdown) {
        return detector.containsMermaid(parser.parse(MarkdownSource.of(markdown)));
    }
}
