@import dev.persefonia.webpublic.PublicHomeViewModel
@param PublicHomeViewModel page
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.productName()}</title>
    @for(String stylesheetPath : page.stylesheetPaths())
        <link rel="stylesheet" href="${stylesheetPath}">
    @endfor
</head>
<body>
    <main>
        <h1>${page.productName()}</h1>
        <p>${page.ownerAlias()}</p>
    </main>
    <script type="module" src="${page.scriptPath()}"></script>
</body>
</html>
