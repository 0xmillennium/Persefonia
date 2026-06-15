@import dev.persefonia.webadmin.taxonomy.AdminTagListPage
@param AdminTagListPage page
@template.admin.components.pageShell("Tags | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
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
`)
