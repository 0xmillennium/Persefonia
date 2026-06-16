@import dev.persefonia.webadmin.content.AdminContentRevisionListPage
@param AdminContentRevisionListPage page
@template.admin.components.pageShell("Revision history | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
<main>
    <h1>Revision history</h1>
    <p><a href="${page.editLink()}">Back to edit</a></p>
    <p>Content: <strong>${page.contentTitle()}</strong></p>
    <p>Status: <strong>${page.contentStatus()}</strong></p>
    @if(page.revisions().isEmpty())
        <p>No revisions have been created yet.</p>
    @else
        <table>
            <thead>
            <tr><th>Revision</th><th>Type</th><th>Title</th><th>Slug</th><th>Created by</th><th>Created at</th><th>Change note</th><th>Rendered HTML</th></tr>
            </thead>
            <tbody>
            @for(var revision : page.revisions())
                <tr>
                    <td>${revision.revisionNumber()}</td>
                    <td>${revision.revisionType()}</td>
                    <td>${revision.title()}</td>
                    <td>${revision.slug()}</td>
                    <td>${revision.createdBy()}</td>
                    <td>${revision.createdAt()}</td>
                    <td>${revision.changeNote() == null ? "None" : revision.changeNote()}</td>
                    <td>${revision.renderedHtmlPresent() ? "Present" : "Absent"}</td>
                </tr>
            @endfor
            </tbody>
        </table>
    @endif
</main>
`)
