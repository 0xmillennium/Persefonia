@import dev.persefonia.webpublic.projects.PublicProjectDetailPage
@param PublicProjectDetailPage page
<!doctype html>
<html lang="${page.htmlLanguage()}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.title()}</title>
    <meta name="description" content="${page.project().summary()}">
    @template.site.components.canonicalLink(page.canonicalUrl())
    @if(page.noindex())
        <meta name="robots" content="noindex, follow">
    @else
        @template.site.components.robotsMeta(false)
    @endif
    @template.site.components.openGraphTags("website", page.project().title(), page.project().summary(), page.canonicalUrl())
    @template.site.components.stylesheetLinks(page.stylesheetPaths())
    @template.site.components.feedDiscoveryLink()
</head>
<body>
    <a class="skip-link" href="#main-content">Skip to main content</a>
    <main id="main-content" class="public-page">
        <article>
            <header>
                <h1>${page.project().title()}</h1>
                <p>${page.project().summary()}</p>
            </header>
            @if(!page.project().technologies().isEmpty())
                <section aria-label="${page.copy().technologiesLabel()}">
                    <h2>${page.copy().technologiesLabel()}</h2>
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
                <section aria-label="${page.copy().tagsLabel()}">
                    <h2>${page.copy().tagsLabel()}</h2>
                    <ul>
                        @for(var tag : page.project().tags())
                            <li>${tag.name()}</li>
                        @endfor
                    </ul>
                </section>
            @endif
            @if(!page.project().links().isEmpty())
                <section aria-label="${page.copy().linksLabel()}">
                    <h2>${page.copy().linksLabel()}</h2>
                    <ul>
                        @for(var link : page.project().links())
                            <li><a href="${link.url()}" target="_blank" rel="noopener noreferrer">${link.label()}</a></li>
                        @endfor
                    </ul>
                </section>
            @endif
            @if(!page.project().sections().isEmpty())
                <h2>${page.copy().caseStudyLabel()}</h2>
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
