@import dev.persefonia.webpublic.projects.PublicProjectDetailPage
@param PublicProjectDetailPage page
<!doctype html>
<html lang="${page.htmlLanguage()}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.title()}</title>
    @template.site.components.canonicalLink(page.canonicalUrl())
    @template.site.components.robotsMeta(page.noindex())
    @template.site.components.stylesheetLinks(page.stylesheetPaths())
</head>
<body>
    <main class="public-page">
        <article>
            <header>
                <h1>${page.project().title()}</h1>
                <p>${page.project().summary()}</p>
            </header>
            @if(!page.project().technologies().isEmpty())
                <section aria-label="Technologies">
                    <h2>Technologies</h2>
                    <ul>
                        @for(var technology : page.project().technologies())
                            <li>
                                <span>${technology.name()}</span>
                                <span>${technology.category()}</span>
                            </li>
                        @endfor
                    </ul>
                </section>
            @endif
            @if(!page.project().tags().isEmpty())
                <section aria-label="Tags">
                    <h2>Tags</h2>
                    <ul>
                        @for(var tag : page.project().tags())
                            <li>${tag.name()}</li>
                        @endfor
                    </ul>
                </section>
            @endif
            @if(!page.project().links().isEmpty())
                <section aria-label="Links">
                    <h2>Links</h2>
                    <ul>
                        @for(var link : page.project().links())
                            <li><a href="${link.url()}" target="_blank" rel="noopener noreferrer">${link.label()}</a></li>
                        @endfor
                    </ul>
                </section>
            @endif
            @if(!page.project().sections().isEmpty())
                @for(var section : page.project().sections())
                    <section>
                        <h2>${section.type()}</h2>
                        <p>${section.body()}</p>
                    </section>
                @endfor
            @endif
        </article>
    </main>
</body>
</html>
