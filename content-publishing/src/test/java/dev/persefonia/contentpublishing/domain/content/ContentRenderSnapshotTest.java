package dev.persefonia.contentpublishing.domain.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentRenderSnapshotTest {
    @Test
    void requiresCoreRenderFields() {
        assertThatThrownBy(() -> snapshot(null, Instant.now(), RendererVersion.of("v1"), ReadingTime.minutes(1), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> snapshot(RenderedHtml.sanitized("<p>x</p>"), null, RendererVersion.of("v1"), ReadingTime.minutes(1), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> snapshot(RenderedHtml.sanitized("<p>x</p>"), Instant.now(), null, ReadingTime.minutes(1), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> snapshot(RenderedHtml.sanitized("<p>x</p>"), Instant.now(), RendererVersion.of("v1"), null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void headingsMayBeEmptyAndAreImmutable() {
        ContentRenderSnapshot snapshot = snapshot(
                RenderedHtml.sanitized("<p>x</p>"),
                Instant.now(),
                RendererVersion.of("v1"),
                ReadingTime.minutes(1),
                new ArrayList<>());

        assertThat(snapshot.headings()).isEmpty();
        assertThatThrownBy(() -> snapshot.headings().add(RenderedHeading.of(1, "Heading", "heading", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void duplicateHeadingPositionsAndAnchorsAreRejected() {
        assertThatThrownBy(() -> snapshot(
                RenderedHtml.sanitized("<p>x</p>"),
                Instant.now(),
                RendererVersion.of("v1"),
                ReadingTime.minutes(1),
                List.of(
                        RenderedHeading.of(2, "One", "one", 1),
                        RenderedHeading.of(2, "Two", "two", 1))))
                .isInstanceOf(ContentValidationException.class);

        assertThatThrownBy(() -> snapshot(
                RenderedHtml.sanitized("<p>x</p>"),
                Instant.now(),
                RendererVersion.of("v1"),
                ReadingTime.minutes(1),
                List.of(
                        RenderedHeading.of(2, "One", "same", 1),
                        RenderedHeading.of(2, "Two", "same", 2))))
                .isInstanceOf(ContentValidationException.class);
    }

    @Test
    void containsMermaidFlagIsPreserved() {
        ContentRenderSnapshot snapshot = ContentRenderSnapshot.of(
                RenderedHtml.sanitized("<pre class=\"mermaid\">graph</pre>"),
                ContentItemTestFixtures.PUBLISHED_AT,
                RendererVersion.of("v1"),
                ReadingTime.minutes(1),
                true,
                List.of());

        assertThat(snapshot.containsMermaid()).isTrue();
    }

    private static ContentRenderSnapshot snapshot(
            RenderedHtml html,
            Instant renderedAt,
            RendererVersion rendererVersion,
            ReadingTime readingTime,
            List<RenderedHeading> headings) {
        return ContentRenderSnapshot.of(html, renderedAt, rendererVersion, readingTime, false, headings);
    }
}
