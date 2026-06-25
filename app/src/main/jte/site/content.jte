@import dev.persefonia.webpublic.content.PublicContentHeadingView
@import dev.persefonia.webpublic.content.PublicContentPage
@import dev.persefonia.webpublic.content.PublicContentTranslationLinkView
@param PublicContentPage page
<!doctype html>
<html lang="${page.htmlLanguage()}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.seoTitle()}</title>
    <meta name="description" content="${page.seoDescription()}">
    @template.site.components.canonicalLink(page.canonicalUrl())
    @template.site.components.hreflangLinks(page.hreflangLinks())
    @template.site.components.openGraphTags(page.openGraphType(), page.openGraphTitle(), page.openGraphDescription(), page.openGraphUrl())
    <meta property="article:published_time" content="${page.publishedAtIso()}">
    <meta property="article:modified_time" content="${page.updatedAtIso()}">
    @template.site.components.robotsMeta(page.noindex())
    @template.site.components.stylesheetLinks(page.stylesheetPaths())
    @template.site.components.feedDiscoveryLink()
</head>
<body>
    <a class="skip-link" href="#main-content">Skip to main content</a>
    <main id="main-content" class="public-page">
        <article class="public-article">
            <header class="public-article__header">
                <p class="public-article__kicker">${page.typeLabel()}</p>
                <h1>${page.displayTitle()}</h1>
                <p class="public-article__summary">${page.summary()}</p>
                <p class="public-article__meta">
                    <time datetime="${page.publishedAtIso()}">${page.publishedAtDisplay()}</time>
                    <span>${page.readingTimeLabel()}</span>
                </p>
            </header>
            @if(!page.translationLinks().isEmpty())
                <nav class="public-translations" aria-label="Translations">
                    <p>Available in:</p>
                    <ul>
                        @for(PublicContentTranslationLinkView link : page.translationLinks())
                            <li>
                                <a href="${link.publicUrl()}" hreflang="${link.language()}" lang="${link.language()}" title="${link.title()}">${link.label()}</a>
                            </li>
                        @endfor
                    </ul>
                </nav>
            @endif
            @if(!page.headings().isEmpty())
                <nav class="public-toc" aria-label="Table of contents">
                    <ol>
                        @for(PublicContentHeadingView heading : page.headings())
                            <li>
                                <a href="${heading.href()}">${heading.text()}</a>
                            </li>
                        @endfor
                    </ol>
                </nav>
            @endif
            <section class="public-content">
                $unsafe{page.renderedHtml()}
            </section>
        </article>
    </main>
    @if(page.mermaidScriptPath().isPresent())
        <script type="module" src="${page.mermaidScriptPath().get()}"></script>
    @endif
</body>
</html>
