@import dev.persefonia.webadmin.taxonomy.AdminTagFormPage
@param AdminTagFormPage page
@template.admin.components.pageShell("Edit tag | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>${page.heading()}</h1>
        <p><a href="/admin/tags">Back to tags</a></p>
        <p>Status: <strong>${page.status()}</strong></p>
        @if(page.hasErrors())
            @for(var error : page.globalErrors())
                <p role="alert">${error}</p>
            @endfor
            @for(var error : page.fieldErrors())
                <p role="alert">${error.field()}: ${error.message()}</p>
            @endfor
        @endif
        @if("ACTIVE".equals(page.status()))
            <form method="post" action="${page.action()}">
                @template.admin.components.csrfField(page.chrome().logoutForm())
                <label for="name">Name</label>
                <input id="name" name="name" maxlength="80" value="${page.form().getName()}">
                <label for="slug">Slug</label>
                <input id="slug" name="slug" maxlength="100" value="${page.form().getSlug()}">
                <label for="description">Description</label>
                <textarea id="description" name="description" maxlength="500">${page.form().getDescription()}</textarea>
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
