@import dev.persefonia.webadmin.taxonomy.AdminTagListPage
@param AdminTagListPage page
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex,nofollow,noarchive">
    <title>Tags | Persefonia Admin</title>
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
    <h1>Tags</h1>
    <p><a href="/admin/tags/new">Create tag</a></p>
    @if(page.successMessage() != null)
        <p role="status">${page.successMessage()}</p>
    @endif
    @if(page.tags().isEmpty())
        <p>No tags yet.</p>
    @else
        <table>
            <thead><tr><th>Name</th><th>Slug</th><th>Status</th><th>Updated</th><th>Actions</th></tr></thead>
            <tbody>
            @for(var tag : page.tags())
                <tr>
                    <td>${tag.name()}</td>
                    <td>${tag.slug()}</td>
                    <td>${tag.status()}</td>
                    <td>${tag.updatedAt().toString()}</td>
                    <td><a href="/admin/tags/${tag.tagId().value().toString()}/edit">Edit</a></td>
                </tr>
            @endfor
            </tbody>
        </table>
    @endif
</main>
</body>
</html>
