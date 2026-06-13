@import dev.persefonia.webpublic.content.PublicContentHeadingView
@import dev.persefonia.webpublic.content.PublicContentPage
@param PublicContentPage page
<!doctype html>
<html lang="${page.htmlLanguage()}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.seoTitle()}</title>
    <meta name="description" content="${page.seoDescription()}">
    <link rel="canonical" href="${page.canonicalUrl()}">
    <meta property="og:title" content="${page.openGraphTitle()}">
    <meta property="og:description" content="${page.openGraphDescription()}">
    <meta property="og:url" content="${page.openGraphUrl()}">
    <meta property="og:type" content="${page.openGraphType()}">
    <meta property="article:published_time" content="${page.publishedAtIso()}">
    <meta property="article:modified_time" content="${page.updatedAtIso()}">
    @if(page.noindex())
        <meta name="robots" content="noindex">
    @endif
    @for(String stylesheetPath : page.stylesheetPaths())
        <link rel="stylesheet" href="${stylesheetPath}">
    @endfor
</head>
<body>
    <main class="public-page">
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
