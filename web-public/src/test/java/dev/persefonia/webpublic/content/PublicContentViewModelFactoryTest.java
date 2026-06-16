package dev.persefonia.webpublic.content;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.query.PublicContentHeadingResult;
import dev.persefonia.contentpublishing.application.query.PublicContentPageResult;
import dev.persefonia.contentpublishing.application.query.PublicHreflangLink;
import dev.persefonia.contentpublishing.application.query.PublicTranslationLink;
import dev.persefonia.contentpublishing.application.query.PublicTranslationLinkSet;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.HeadingAnchor;
import dev.persefonia.contentpublishing.domain.content.HeadingLevel;
import dev.persefonia.contentpublishing.domain.content.HeadingText;
import dev.persefonia.contentpublishing.domain.content.OpenGraphDescription;
import dev.persefonia.contentpublishing.domain.content.OpenGraphTitle;
import dev.persefonia.contentpublishing.domain.content.ReadingTime;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import dev.persefonia.contentpublishing.domain.content.RendererVersion;
import dev.persefonia.contentpublishing.domain.content.SeoDescription;
import dev.persefonia.contentpublishing.domain.content.SeoTitle;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.SortOrder;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.webpublic.FrontendAssetResolver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PublicContentViewModelFactoryTest {
    private final PublicContentViewModelFactory factory = new PublicContentViewModelFactory(
            new StubAssetResolver(),
            new PublicCanonicalUrlFactory("https://example.test/"));

    @Test
    void mapsMetadataFieldsWithExplicitValues() {
        PublicContentPage page = factory.contentPage(page(
                ContentType.ARTICLE,
                ContentVisibility.PUBLIC,
                Optional.of(SeoTitle.of("Custom SEO")),
                Optional.of(SeoDescription.of("Custom SEO description")),
                Optional.of(OpenGraphTitle.of("Custom OG")),
                Optional.of(OpenGraphDescription.of("Custom OG description")),
                false),
                PublicTranslationLinkSet.empty());

        assertThat(page.seoTitle()).isEqualTo("Custom SEO");
        assertThat(page.seoDescription()).isEqualTo("Custom SEO description");
        assertThat(page.openGraphTitle()).isEqualTo("Custom OG");
        assertThat(page.openGraphDescription()).isEqualTo("Custom OG description");
        assertThat(page.canonicalUrl()).isEqualTo("https://example.test/tr/articles/public-title");
        assertThat(page.openGraphUrl()).isEqualTo(page.canonicalUrl());
        assertThat(page.openGraphType()).isEqualTo("article");
        assertThat(page.publishedAtIso()).isEqualTo("2026-06-12T12:00:01Z");
        assertThat(page.updatedAtIso()).isEqualTo("2026-06-12T12:00:02Z");
        assertThat(page.publishedAtDisplay()).isEqualTo("2026-06-12");
        assertThat(page.readingTimeLabel()).isEqualTo("4 min read");
        assertThat(page.noindex()).isFalse();
    }

    @Test
    void fallsBackToContentTitleAndSummaryWhenMetadataIsMissing() {
        PublicContentPage page = factory.contentPage(page(
                ContentType.PAGE,
                ContentVisibility.PUBLIC,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false),
                PublicTranslationLinkSet.empty());

        assertThat(page.seoTitle()).isEqualTo("Public Title");
        assertThat(page.seoDescription()).isEqualTo("Public summary");
        assertThat(page.openGraphTitle()).isEqualTo("Public Title");
        assertThat(page.openGraphDescription()).isEqualTo("Public summary");
        assertThat(page.openGraphType()).isEqualTo("website");
    }

    @Test
    void openGraphFallsBackToSeoValuesBeforeContentValues() {
        PublicContentPage page = factory.contentPage(page(
                ContentType.NOTE,
                ContentVisibility.PUBLIC,
                Optional.of(SeoTitle.of("SEO fallback")),
                Optional.of(SeoDescription.of("SEO description fallback")),
                Optional.empty(),
                Optional.empty(),
                false),
                PublicTranslationLinkSet.empty());

        assertThat(page.openGraphTitle()).isEqualTo("SEO fallback");
        assertThat(page.openGraphDescription()).isEqualTo("SEO description fallback");
        assertThat(page.openGraphType()).isEqualTo("article");
    }

    @Test
    void unlistedContentIsNoindexAndMermaidScriptIsConditional() {
        PublicContentPage unlisted = factory.contentPage(page(
                ContentType.ARTICLE,
                ContentVisibility.UNLISTED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true),
                PublicTranslationLinkSet.empty());
        PublicContentPage publicPage = factory.contentPage(page(
                ContentType.ARTICLE,
                ContentVisibility.PUBLIC,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false),
                PublicTranslationLinkSet.empty());

        assertThat(unlisted.noindex()).isTrue();
        assertThat(unlisted.mermaidScriptPath()).contains("/assets/mermaid-loader-test.js");
        assertThat(publicPage.noindex()).isFalse();
        assertThat(publicPage.mermaidScriptPath()).isEmpty();
    }

    @Test
    void mapsPersistedHeadingsWithoutDerivingThemFromHtml() {
        PublicContentPage page = factory.contentPage(page(
                ContentType.RESEARCH,
                ContentVisibility.PUBLIC,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false),
                PublicTranslationLinkSet.empty());

        assertThat(page.headings()).singleElement().satisfies(heading -> {
            assertThat(heading.level()).isEqualTo(2);
            assertThat(heading.text()).isEqualTo("Heading <Escaped>");
            assertThat(heading.anchor()).isEqualTo("heading-escaped");
            assertThat(heading.href()).isEqualTo("#heading-escaped");
            assertThat(heading.position()).isEqualTo(1);
        });
    }

    @Test
    void mapsTranslationLinksAndHreflangLinks() {
        PublicContentPage page = factory.contentPage(page(
                        ContentType.ARTICLE,
                        ContentVisibility.PUBLIC,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        false),
                PublicTranslationLinkSet.withAlternates(
                        List.of(new PublicTranslationLink(
                                "en",
                                "English",
                                "English <Title>",
                                "/en/articles/public-title",
                                "https://example.test/en/articles/public-title")),
                        List.of(
                                new PublicHreflangLink("tr", "https://example.test/tr/articles/public-title"),
                                new PublicHreflangLink("en", "https://example.test/en/articles/public-title"))));

        assertThat(page.translationLinks()).singleElement().satisfies(link -> {
            assertThat(link.language()).isEqualTo("en");
            assertThat(link.label()).isEqualTo("English");
            assertThat(link.title()).isEqualTo("English <Title>");
            assertThat(link.publicUrl()).isEqualTo("/en/articles/public-title");
        });
        assertThat(page.hreflangLinks())
                .extracting(PublicContentHreflangLinkView::languageCode)
                .containsExactly("tr", "en");
    }

    private static PublicContentPageResult page(
            ContentType type,
            ContentVisibility visibility,
            Optional<SeoTitle> seoTitle,
            Optional<SeoDescription> seoDescription,
            Optional<OpenGraphTitle> openGraphTitle,
            Optional<OpenGraphDescription> openGraphDescription,
            boolean containsMermaid) {
        return new PublicContentPageResult(
                ContentId.newId(),
                type,
                ContentLanguage.TR,
                visibility,
                Slug.of("public-title"),
                Title.of("Public Title"),
                Summary.of("Public summary"),
                CanonicalPath.of("/tr/articles/public-title"),
                seoTitle,
                seoDescription,
                openGraphTitle,
                openGraphDescription,
                Instant.parse("2026-06-12T12:00:01Z"),
                Instant.parse("2026-06-12T12:00:02Z"),
                RenderedHtml.sanitized("<h2 id=\"heading-escaped\">Heading</h2>"),
                RendererVersion.of("test-renderer"),
                ReadingTime.minutes(4),
                containsMermaid,
                List.of(new PublicContentHeadingResult(
                        HeadingLevel.of(2),
                        HeadingText.of("Heading <Escaped>"),
                        HeadingAnchor.of("heading-escaped"),
                        SortOrder.of(1))));
    }

    private static final class StubAssetResolver implements FrontendAssetResolver {
        @Override
        public String scriptPath(String entry) {
            if ("src/mermaid-loader.ts".equals(entry)) {
                return "/assets/mermaid-loader-test.js";
            }
            return "/assets/main-test.js";
        }

        @Override
        public List<String> stylesheetPaths(String entry) {
            return List.of("/assets/main-test.css");
        }
    }
}
