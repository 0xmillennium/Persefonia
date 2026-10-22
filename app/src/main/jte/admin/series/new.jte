@import dev.persefonia.webadmin.FieldErrors
@import dev.persefonia.webadmin.series.AdminSeriesFormPage
@param AdminSeriesFormPage page
@param java.util.List<String> adminStylesheetPaths = java.util.List.of()
@template.admin.components.pageShell("Create series | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, adminStylesheetPaths = adminStylesheetPaths, content = @`
    <main id="admin-content" tabindex="-1">
        <h1>${page.heading()}</h1>
        <p><a href="/admin/series">Back to series</a></p>
        @if(page.hasErrors())
            @if(!page.globalErrors().isEmpty())
                <section id="series-errors" role="alert" tabindex="-1" autofocus aria-label="Series errors">
                    @for(var error : page.globalErrors())
                        <p><a href="#series">${error}</a></p>
                    @endfor
                </section>
            @endif
            @template.admin.components.fieldErrors(page.fieldErrors())
        @endif
        <form id="series" tabindex="-1" aria-label="Create series" aria-describedby="${page.globalErrors().isEmpty() ? null : "series-errors"}" method="post" action="${page.action()}">
            @template.admin.components.csrfField(page.chrome().logoutForm())
            <label for="language">Language</label>
            <select id="language" name="language" aria-invalid="${FieldErrors.has(page.fieldErrors(), "language") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "language") ? "error-language" : null}">
                <option value="EN" selected="${"EN".equals(page.form().getLanguage())}">EN</option>
                <option value="TR" selected="${"TR".equals(page.form().getLanguage())}">TR</option>
            </select>
            <label for="title">Title</label>
            <input id="title" name="title" maxlength="120" value="${page.form().getTitle()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "title") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "title") ? "error-title" : null}">
            <label for="slug">Slug</label>
            <input id="slug" name="slug" maxlength="100" value="${page.form().getSlug()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "slug") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "slug") ? "error-slug" : null}">
            <label for="description">Description</label>
            <textarea id="description" name="description" maxlength="500" aria-invalid="${FieldErrors.has(page.fieldErrors(), "description") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "description") ? "error-description" : null}">${page.form().getDescription()}</textarea>
            <button type="submit">Create series</button>
        </form>
    </main>
`)
