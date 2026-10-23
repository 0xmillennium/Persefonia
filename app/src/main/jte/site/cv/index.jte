@import dev.persefonia.webpublic.cv.PublicCvPage
@param PublicCvPage page
@param String publicNavigationScriptPath = null
@param java.util.List<dev.persefonia.webpublic.navigation.PublicNavigationLink> navigationLinks = java.util.List.of()
<!doctype html>
<html translate="no" lang="${page.htmlLanguage()}">
<head>
    @template.site.components.translationPolicy()
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.title()}</title>
    <meta name="description" content="${page.displayLabel()}">
    @template.site.components.canonicalLink(page.canonicalUrl())
    @template.site.components.robotsMeta(false)
    @template.site.components.openGraphTags("website", page.title(), page.displayLabel(), page.canonicalUrl())
    @template.site.components.standardPublicStyles(page.stylesheetPaths(), publicNavigationScriptPath)
    @template.site.components.feedDiscoveryLink()
</head>
<body>
    <a class="skip-link" href="#main-content">Skip to main content</a>
    @template.site.components.publicHeader("/" + page.language().toLowerCase(java.util.Locale.ROOT) + "/projects", "")
    <main id="main-content" class="public-page public-page--cv">
        <section aria-labelledby="cv-title">
            <h1 id="cv-title">${page.title()}</h1>
            <p>${page.displayLabel()}</p>
            <dl>
                <div>
                    <dt>Language</dt>
                    <dd>${page.language()}</dd>
                </div>
                <div>
                    <dt>File</dt>
                    <dd>${page.displayFilename()}</dd>
                </div>
                <div>
                    <dt>Type</dt>
                    <dd>${page.contentType()}</dd>
                </div>
                <div>
                    <dt>Size</dt>
                    <dd>${page.sizeLabel()}</dd>
                </div>
                <div>
                    <dt>Selected</dt>
                    <dd>${page.selectedAt()}</dd>
                </div>
                <div>
                    <dt>Updated</dt>
                    <dd>${page.assetUpdatedAt()}</dd>
                </div>
            </dl>
            <a href="${page.downloadPath()}">Download ${page.displayFilename()}</a>
        </section>
    </main>
    @template.site.components.publicFooter(navigationLinks)
</body>
</html>
