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
        @if(page.profileAvailable())
            <section aria-label="Profile"></section>
        @endif
    </main>
    <script type="module" src="${page.scriptPath()}"></script>
</body>
</html>
