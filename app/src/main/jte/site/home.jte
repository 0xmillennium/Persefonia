@import dev.persefonia.webpublic.PublicHomeViewModel
@param PublicHomeViewModel page
<!doctype html>
<html lang="${page.defaultLanguage().toLowerCase(java.util.Locale.ROOT)}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="${page.defaultMetaDescription()}">
    <meta name="color-scheme" content="${"SYSTEM".equals(page.defaultTheme()) ? "light dark" : page.defaultTheme().toLowerCase(java.util.Locale.ROOT)}">
    <title>${page.pageTitle()}</title>
    @template.site.components.stylesheetLinks(page.stylesheetPaths())
</head>
<body>
    <main>
        <h1>${page.siteName()}</h1>
        <p>${page.ownerAlias()}</p>
        @if(page.profile().isPresent())
            <section aria-label="Profile">
                <h2>${page.profile().get().displayName()}</h2>
                <p>${page.profile().get().shortBio()}</p>
                @if(!page.profile().get().locationText().isBlank())
                    <p>${page.profile().get().locationText()}</p>
                @endif
                @if(!page.profile().get().technicalFocusAreas().isEmpty())
                    <ul>
                        @for(var area : page.profile().get().technicalFocusAreas())
                            <li>
                                <strong>${area.name()}</strong>
                                @if(!area.description().isBlank())
                                    <span>${area.description()}</span>
                                @endif
                            </li>
                        @endfor
                    </ul>
                @endif
                @if(!page.profile().get().currentFocusItems().isEmpty())
                    <ul>
                        @for(var item : page.profile().get().currentFocusItems())
                            <li>${item.text()}</li>
                        @endfor
                    </ul>
                @endif
                @if(!page.profile().get().educationSummaries().isEmpty())
                    <ul>
                        @for(var summary : page.profile().get().educationSummaries())
                            <li>
                                <span>${summary.institution()}</span>
                                <span>${summary.program()}</span>
                                @if(!summary.description().isBlank())
                                    <span>${summary.description()}</span>
                                @endif
                            </li>
                        @endfor
                    </ul>
                @endif
                @if(!page.profile().get().externalLinks().isEmpty())
                    <ul>
                        @for(var link : page.profile().get().externalLinks())
                            <li><a href="${link.url()}" target="_blank" rel="noopener noreferrer">${link.label()}</a></li>
                        @endfor
                    </ul>
                @endif
            </section>
        @endif
        @if(page.showFeaturedProjects() && !page.featuredProjects().isEmpty())
            <section aria-label="Featured projects">
                <h2>Featured projects</h2>
                <ol>
                    @for(var project : page.featuredProjects())
                        <li>
                            <article>
                                <h3><a href="${project.publicUrl()}">${project.title()}</a></h3>
                                <p>${project.summary()}</p>
                                @if(!project.technologies().isEmpty())
                                    <ul>
                                        @for(var technology : project.technologies())
                                            <li>${technology.name()}</li>
                                        @endfor
                                    </ul>
                                @endif
                            </article>
                        </li>
                    @endfor
                </ol>
            </section>
        @endif
    </main>
    <script type="module" src="${page.scriptPath()}"></script>
</body>
</html>
