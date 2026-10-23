@import dev.persefonia.webadmin.content.AdminContentListPage
@param AdminContentListPage page
@param java.util.List<String> adminStylesheetPaths = java.util.List.of()
@template.admin.components.pageShell("Content | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, adminStylesheetPaths = adminStylesheetPaths, content = @`
<main id="admin-content" tabindex="-1">
    <h1>Content</h1>
    <p><a href="/admin/content/new">Create draft</a></p>
    @if(page.successMessage() != null)
        <p role="status">${page.successMessage()}</p>
    @endif
    @if(page.items().isEmpty())
        <p>No manageable content yet.</p>
    @else
        <div class="admin-table-scroll" role="region" aria-label="Scrollable table" tabindex="0"><table>
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
        </table></div>
    @endif
</main>
`)
