@import dev.persefonia.webpublic.tags.PublicTagContentItemView
@import dev.persefonia.webpublic.tags.PublicTagPage
@param PublicTagPage page
<!doctype html>
<html lang="${page.htmlLanguage()}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.name()}</title>
    <meta name="description" content="${page.description().isBlank() ? page.name() : page.description()}">
    @template.site.components.canonicalLink(page.canonicalUrl())
    @template.site.components.robotsMeta(page.noindex())
    @template.site.components.stylesheetLinks(page.stylesheetPaths())
    @template.site.components.feedDiscoveryLink()
</head>
<body>
    <a class="skip-link" href="#main-content">Skip to main content</a>
    <main id="main-content" class="public-page">
        <header>
            <h1>${page.name()}</h1>
            @if(!page.description().isBlank())
                <p>${page.description()}</p>
            @endif
        </header>
        @if(page.items().isEmpty())
            <p>No public content is currently available for this tag.</p>
        @else
            <ol>
                @for(PublicTagContentItemView item : page.items())
                    <li>
                        <article>
                            <h2><a href="${item.publicUrl()}">${item.title()}</a></h2>
                            <p>${item.summary()}</p>
                            <time datetime="${item.publishedAt()}">${item.publishedAt()}</time>
                        </article>
                    </li>
                @endfor
            </ol>
        @endif
    </main>
</body>
</html>
