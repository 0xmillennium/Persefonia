@import dev.persefonia.profileportfolio.application.query.PublicProjectCardView
@import dev.persefonia.webpublic.projects.PublicProjectListingPage
@param PublicProjectListingPage page
<!doctype html>
<html lang="${page.htmlLanguage()}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.title()}</title>
    <meta name="description" content="${page.copy().projectsMetaDescription()}">
    @template.site.components.canonicalLink(page.canonicalUrl())
    @template.site.components.robotsMeta(page.noindex())
    @template.site.components.openGraphTags("website", page.title(), page.copy().projectsMetaDescription(), page.canonicalUrl())
    @template.site.components.stylesheetLinks(page.stylesheetPaths())
    @template.site.components.feedDiscoveryLink()
</head>
<body>
    <a class="skip-link" href="#main-content">Skip to main content</a>
    <main id="main-content" class="public-page">
        <header>
            <h1>${page.title()}</h1>
        </header>
        @if(page.projects().isEmpty())
            <p>${page.copy().projectsEmptyMessage()}</p>
        @else
            <ol>
                @for(PublicProjectCardView project : page.projects())
                    <li>
                        <article>
                            <h2><a href="${project.publicUrl()}">${project.title()}</a></h2>
                            <p>${project.summary()}</p>
                            @if(!project.technologies().isEmpty())
                                <h3>${page.copy().technologiesLabel()}</h3>
                                <ul>
                                    @for(var technology : project.technologies())
                                        <li>${technology.name()}</li>
                                    @endfor
                                </ul>
                            @endif
                            @if(!project.tags().isEmpty())
                                <h3>${page.copy().tagsLabel()}</h3>
                                <ul>
                                    @for(var tag : project.tags())
                                        <li>${tag.name()}</li>
                                    @endfor
                                </ul>
                            @endif
                        </article>
                    </li>
                @endfor
            </ol>
        @endif
    </main>
</body>
</html>
