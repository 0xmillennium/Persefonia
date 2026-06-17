@import dev.persefonia.webadmin.projects.AdminProjectListPage
@param AdminProjectListPage page
@template.admin.components.pageShell("Projects | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>Projects</h1>
        <p><a href="/admin/projects/new">Create project</a></p>
        @if(page.successMessage() != null)
            <p role="status">${page.successMessage()}</p>
        @endif
        @if(page.projects().isEmpty())
            <p>No projects yet.</p>
        @else
            <table>
                <thead><tr><th>Title</th><th>Status</th><th>Visibility</th><th>Featured</th><th>Sort</th><th>Updated</th><th>Actions</th></tr></thead>
                <tbody>
                @for(var project : page.projects())
                    <tr>
                        <td>${project.title()}</td>
                        <td>${project.status()}</td>
                        <td>${project.visibility()}</td>
                        <td>${project.featured() ? "Yes" : "No"}</td>
                        <td>${project.sortOrder() == null ? "" : project.sortOrder().toString()}</td>
                        <td>${project.updatedAt().toString()}</td>
                        <td><a href="/admin/projects/${project.id().toString()}/edit">Edit</a></td>
                    </tr>
                @endfor
                </tbody>
            </table>
        @endif
    </main>
`)
