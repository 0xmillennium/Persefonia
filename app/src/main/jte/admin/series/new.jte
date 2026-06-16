@import dev.persefonia.webadmin.series.AdminSeriesFormPage
@param AdminSeriesFormPage page
@template.admin.components.pageShell("Create series | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>${page.heading()}</h1>
        <p><a href="/admin/series">Back to series</a></p>
        @if(page.hasErrors())
            @for(var error : page.globalErrors())
                <p role="alert">${error}</p>
            @endfor
            @for(var error : page.fieldErrors())
                <p role="alert">${error.field()}: ${error.message()}</p>
            @endfor
        @endif
        <form method="post" action="${page.action()}">
            @template.admin.components.csrfField(page.chrome().logoutForm())
            <label for="language">Language</label>
            <select id="language" name="language">
                <option value="EN" selected="${"EN".equals(page.form().getLanguage())}">EN</option>
                <option value="TR" selected="${"TR".equals(page.form().getLanguage())}">TR</option>
            </select>
            <label for="title">Title</label>
            <input id="title" name="title" maxlength="120" value="${page.form().getTitle()}">
            <label for="slug">Slug</label>
            <input id="slug" name="slug" maxlength="100" value="${page.form().getSlug()}">
            <label for="description">Description</label>
            <textarea id="description" name="description" maxlength="500">${page.form().getDescription()}</textarea>
            <button type="submit">Create series</button>
        </form>
    </main>
`)
