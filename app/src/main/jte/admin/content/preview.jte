@import dev.persefonia.webadmin.content.AdminContentPreviewPage
@param AdminContentPreviewPage page
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex,nofollow">
    <title>${page.heading()} | Persefonia Admin</title>
</head>
<body>
<header>
    <p>Signed in as <strong>${page.chrome().admin().displayName()}</strong></p>
    <form method="post" action="${page.chrome().logoutForm().action()}">
        <input type="hidden" name="${page.chrome().logoutForm().csrfParameterName()}" value="${page.chrome().logoutForm().csrfToken()}">
        <button type="submit">Logout</button>
    </form>
</header>
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
