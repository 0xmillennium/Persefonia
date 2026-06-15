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
@template.admin.components.adminHeader(page.chrome().admin(), page.chrome().logoutForm(), false)
@template.admin.components.adminNavigation(page.chrome().navigation())
<main>
    <h1>Content</h1>
    <p><a href="/admin/content/new">Create draft</a></p>
    @if(page.successMessage() != null)
        <p role="status">${page.successMessage()}</p>
    @endif
    @if(page.items().isEmpty())
        <p>No manageable content yet.</p>
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
