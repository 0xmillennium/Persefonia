package dev.persefonia.contentpublishing.domain.content;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ContentRenderSnapshot {
    private final RenderedHtml renderedHtml;
    private final Instant renderedAt;
    private final RendererVersion rendererVersion;
    private final ReadingTime readingTime;
    private final boolean containsMermaid;
    private final List<RenderedHeading> headings;

    public ContentRenderSnapshot(
            RenderedHtml renderedHtml,
            Instant renderedAt,
            RendererVersion rendererVersion,
            ReadingTime readingTime,
            boolean containsMermaid,
            List<RenderedHeading> headings) {
        this.renderedHtml = Objects.requireNonNull(renderedHtml, "renderedHtml");
        this.renderedAt = Objects.requireNonNull(renderedAt, "renderedAt");
        this.rendererVersion = Objects.requireNonNull(rendererVersion, "rendererVersion");
        this.readingTime = Objects.requireNonNull(readingTime, "readingTime");
        this.containsMermaid = containsMermaid;
        this.headings = List.copyOf(Objects.requireNonNull(headings, "headings"));
        rejectDuplicateHeadingAnchors(this.headings);
        rejectDuplicateHeadingPositions(this.headings);
    }

    public static ContentRenderSnapshot of(
            RenderedHtml renderedHtml,
            Instant renderedAt,
            RendererVersion rendererVersion,
            ReadingTime readingTime,
            boolean containsMermaid,
            List<RenderedHeading> headings) {
        return new ContentRenderSnapshot(renderedHtml, renderedAt, rendererVersion, readingTime, containsMermaid, headings);
    }

    private static void rejectDuplicateHeadingAnchors(List<RenderedHeading> headings) {
        Set<HeadingAnchor> anchors = new HashSet<>();
        for (RenderedHeading heading : headings) {
            if (!anchors.add(heading.anchor())) {
                throw new ContentValidationException("heading anchors must be unique");
            }
        }
    }

    private static void rejectDuplicateHeadingPositions(List<RenderedHeading> headings) {
        Set<SortOrder> positions = new HashSet<>();
        for (RenderedHeading heading : headings) {
            if (!positions.add(heading.position())) {
                throw new ContentValidationException("heading positions must be unique");
            }
        }
    }

    public RenderedHtml renderedHtml() {
        return renderedHtml;
    }

    public Instant renderedAt() {
        return renderedAt;
    }

    public RendererVersion rendererVersion() {
        return rendererVersion;
    }

    public ReadingTime readingTime() {
        return readingTime;
    }

    public boolean containsMermaid() {
        return containsMermaid;
    }

    public List<RenderedHeading> headings() {
        return List.copyOf(headings);
    }
}
