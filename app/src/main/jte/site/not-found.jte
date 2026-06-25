@import dev.persefonia.webpublic.content.PublicNotFoundPage
@param PublicNotFoundPage page
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.title()}</title>
    @template.site.components.robotsMeta(page.noindex())
    @template.site.components.stylesheetLinks(page.stylesheetPaths())
</head>
<body>
    <a class="skip-link" href="#main-content">Skip to main content</a>
    <main id="main-content" class="public-page public-page--not-found">
        <section class="public-not-found" aria-labelledby="not-found-title">
            <h1 id="not-found-title">${page.title()}</h1>
            <p>${page.message()}</p>
        </section>
    </main>
</body>
</html>
