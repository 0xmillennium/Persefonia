@import dev.persefonia.webadmin.FieldErrors
@import dev.persefonia.webadmin.taxonomy.AdminTagFormPage
@param AdminTagFormPage page
@param java.util.List<String> adminStylesheetPaths = java.util.List.of()
@template.admin.components.pageShell("Edit tag | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, adminStylesheetPaths = adminStylesheetPaths, content = @`
    <main id="admin-content" tabindex="-1">
        <h1>${page.heading()}</h1>
        <p><a href="/admin/tags">Back to tags</a></p>
        <p>Status: <strong>${page.status()}</strong></p>
        @if(page.hasErrors())
            @for(var error : page.globalErrors())
                <p role="alert">${error}</p>
            @endfor
            @template.admin.components.fieldErrors(page.fieldErrors())
        @endif
        @if("ACTIVE".equals(page.status()))
            <form method="post" action="${page.action()}">
                @template.admin.components.csrfField(page.chrome().logoutForm())
                <label for="name">Name</label>
                <input id="name" name="name" maxlength="80" value="${page.form().getName()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "name") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "name") ? "error-name" : null}">
                <label for="slug">Slug</label>
                <input id="slug" name="slug" maxlength="100" value="${page.form().getSlug()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "slug") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "slug") ? "error-slug" : null}">
                <label for="description">Description</label>
                <textarea id="description" name="description" maxlength="500" aria-invalid="${FieldErrors.has(page.fieldErrors(), "description") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "description") ? "error-description" : null}">${page.form().getDescription()}</textarea>
                <button type="submit">Save changes</button>
            </form>
        @else
            <p>Archived tags are read-only.</p>
        @endif
        @if(page.archiveAction() != null)
            <form method="post" action="${page.archiveAction()}">
                @template.admin.components.csrfField(page.chrome().logoutForm())
                <button type="submit">Archive</button>
            </form>
        @endif
    </main>
`)
