@import dev.persefonia.webpublic.series.PublicSeriesEntryView
@import dev.persefonia.webpublic.series.PublicSeriesPage
@param PublicSeriesPage page
<!doctype html>
<html lang="${page.htmlLanguage()}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.title()}</title>
    <meta name="description" content="${page.description().isBlank() ? page.title() : page.description()}">
    @template.site.components.canonicalLink(page.canonicalUrl())
    @template.site.components.robotsMeta(page.noindex())
    @template.site.components.stylesheetLinks(page.stylesheetPaths())
    @template.site.components.feedDiscoveryLink()
</head>
<body>
    <a class="skip-link" href="#main-content">Skip to main content</a>
    <main id="main-content" class="public-page">
        <header>
            <h1>${page.title()}</h1>
            @if(!page.description().isBlank())
                <p>${page.description()}</p>
            @endif
        </header>
        @if(page.entries().isEmpty())
            <p>No public content is currently available for this series.</p>
        @else
            <ol>
                @for(PublicSeriesEntryView entry : page.entries())
                    <li value="${entry.position()}">
                        <article>
                            <h2><a href="${entry.publicUrl()}">${entry.title()}</a></h2>
                            <p>${entry.summary()}</p>
                            <time datetime="${entry.publishedAt()}">${entry.publishedAt()}</time>
                        </article>
                    </li>
                @endfor
            </ol>
        @endif
    </main>
</body>
</html>
