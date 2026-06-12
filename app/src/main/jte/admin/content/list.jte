@import dev.persefonia.webadmin.content.AdminContentListPage
@param AdminContentListPage page
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex,nofollow,noarchive">
    <title>Content | Persefonia Admin</title>
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
    <h1>Content</h1>
    <p><a href="/admin/content/new">Create draft</a></p>
    @if(page.successMessage() != null)
        <p role="status">${page.successMessage()}</p>
    @endif
    @if(page.items().isEmpty())
        <p>No editable content yet.</p>
    @else
        <table>
            <thead><tr><th>Title</th><th>Status</th><th>Visibility</th><th>Language</th><th>Updated</th><th>Actions</th></tr></thead>
            <tbody>
            @for(var item : page.items())
                <tr>
                    <td>${item.title()} <small>${item.slug()}</small></td>
                    <td>${item.status()}</td>
                    <td>${item.visibility()}</td>
                    <td>${item.language()}</td>
                    <td>${item.updatedAt()}</td>
                    <td>
                        <a href="${item.editLink()}">Edit</a>
                        @if(item.previewAvailable())
                            <a href="${item.previewLink()}">Preview</a>
                        @endif
                    </td>
                </tr>
            @endfor
            </tbody>
        </table>
    @endif
</main>
</body>
</html>
