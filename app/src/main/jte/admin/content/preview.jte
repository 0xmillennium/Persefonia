@import dev.persefonia.webadmin.content.AdminContentPreviewPage
@param AdminContentPreviewPage page
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex,nofollow,noarchive">
    <title>${page.heading()} | Persefonia Admin</title>
</head>
<body>
@template.admin.components.adminHeader(page.chrome().admin(), page.chrome().logoutForm(), false)
<main>
    <h1>${page.heading()}</h1>
    <p><a href="${page.editLink()}">Back to edit</a></p>
    @for(var error : page.globalErrors())
        <p role="alert">${error}</p>
    @endfor
    @if(page.available())
        @if(page.containsMermaid())
            <p>Mermaid blocks are shown as code and are not executed.</p>
        @endif
        <article>$unsafe{page.sanitizedHtml()}</article>
    @endif
</main>
</body>
</html>
