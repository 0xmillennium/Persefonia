@import dev.persefonia.webpublic.content.PublicNotFoundPage
@param PublicNotFoundPage page
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.title()}</title>
    @if(page.noindex())
        <meta name="robots" content="noindex">
    @endif
    @for(String stylesheetPath : page.stylesheetPaths())
        <link rel="stylesheet" href="${stylesheetPath}">
    @endfor
</head>
<body>
    <main class="public-page public-page--not-found">
        <section class="public-not-found" aria-labelledby="not-found-title">
            <h1 id="not-found-title">${page.title()}</h1>
            <p>${page.message()}</p>
        </section>
    </main>
</body>
</html>
