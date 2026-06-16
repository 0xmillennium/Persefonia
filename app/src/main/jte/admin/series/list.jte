@import dev.persefonia.webadmin.series.AdminSeriesListPage
@param AdminSeriesListPage page
@template.admin.components.pageShell("Series | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>Series</h1>
        <p><a href="/admin/series/new">Create series</a></p>
        @if(page.successMessage() != null)
            <p role="status">${page.successMessage()}</p>
        @endif
        @if(page.series().isEmpty())
            <p>No series yet.</p>
        @else
            <table>
                <thead><tr><th>Title</th><th>Language</th><th>Slug</th><th>Status</th><th>Entries</th><th>Updated</th><th>Actions</th></tr></thead>
                <tbody>
                @for(var series : page.series())
                    <tr>
                        <td>${series.title()}</td>
                        <td>${series.language().name()}</td>
                        <td>${series.slug()}</td>
                        <td>${series.status().name()}</td>
                        <td>${series.entryCount()}</td>
                        <td>${series.updatedAt().toString()}</td>
                        <td><a href="/admin/series/${series.id().value().toString()}/edit">Edit</a></td>
                    </tr>
                @endfor
                </tbody>
            </table>
        @endif
    </main>
`)
