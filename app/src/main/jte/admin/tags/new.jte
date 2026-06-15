@import dev.persefonia.webadmin.taxonomy.AdminTagFormPage
@param AdminTagFormPage page
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex,nofollow,noarchive">
    <title>Create tag | Persefonia Admin</title>
</head>
<body>
<header>
    <p>Signed in as <strong>${page.chrome().admin().displayName()}</strong></p>
    <form method="post" action="${page.chrome().logoutForm().action()}">
        <input type="hidden" name="${page.chrome().logoutForm().csrfParameterName()}" value="${page.chrome().logoutForm().csrfToken()}">
        <button type="submit">Logout</button>
    </form>
</header>
<nav aria-label="Admin navigation">
    <ul>
        @for(var item : page.chrome().navigation())
            <li>
                @if(item.disabled())
                    <span aria-disabled="true">${item.label()}</span>
                @else
                    <a href="${item.href()}" aria-current="${item.active() ? "page" : null}">${item.label()}</a>
                @endif
            </li>
        @endfor
    </ul>
</nav>
<main>
    <h1>${page.heading()}</h1>
    <p><a href="/admin/tags">Back to tags</a></p>
    @if(page.hasErrors())
        @for(var error : page.globalErrors())
            <p role="alert">${error}</p>
        @endfor
        @for(var error : page.fieldErrors())
            <p role="alert">${error.field()}: ${error.message()}</p>
        @endfor
    @endif
    <form method="post" action="${page.action()}">
        <input type="hidden" name="${page.chrome().logoutForm().csrfParameterName()}" value="${page.chrome().logoutForm().csrfToken()}">
        <label for="name">Name</label>
        <input id="name" name="name" maxlength="80" value="${page.form().getName()}">
        <label for="slug">Slug</label>
        <input id="slug" name="slug" maxlength="100" value="${page.form().getSlug()}">
        <label for="description">Description</label>
        <textarea id="description" name="description" maxlength="500">${page.form().getDescription()}</textarea>
        <button type="submit">Create tag</button>
    </form>
</main>
</body>
</html>
