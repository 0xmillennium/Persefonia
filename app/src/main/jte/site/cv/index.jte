@import dev.persefonia.webpublic.cv.PublicCvPage
@param PublicCvPage page
<!doctype html>
<html lang="${page.language()}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.title()}</title>
    @template.site.components.feedDiscoveryLink()
</head>
<body>
    <main class="public-page public-page--cv">
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
</body>
</html>
