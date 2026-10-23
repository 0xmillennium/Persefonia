@import dev.persefonia.webpublic.content.PublicNotFoundPage
@param PublicNotFoundPage page
@param String publicNavigationScriptPath = null
@param java.util.List<dev.persefonia.webpublic.navigation.PublicNavigationLink> navigationLinks = java.util.List.of()
<!doctype html>
<html translate="no" lang="${page.htmlLanguage()}">
<head>
    @template.site.components.translationPolicy()
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.title()}</title>
    @template.site.components.robotsMeta(page.noindex())
    @template.site.components.standardPublicStyles(page.stylesheetPaths(), publicNavigationScriptPath)
</head>
<body>
    <a class="skip-link" href="#main-content">Skip to main content</a>
    @template.site.components.publicHeader("/" + "en" + "/projects", "")
    <main id="main-content" class="public-page public-page--not-found">
        <section class="public-not-found" aria-labelledby="not-found-title">
            <h1 id="not-found-title">${page.title()}</h1>
            <p>${page.message()}</p>
        </section>
    </main>
    @template.site.components.publicFooter(navigationLinks)
</body>
</html>
