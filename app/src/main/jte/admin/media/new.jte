@import dev.persefonia.webadmin.FieldErrors
@import dev.persefonia.webadmin.media.AdminMediaUploadPage
@param AdminMediaUploadPage page
@param java.util.List<String> adminStylesheetPaths = java.util.List.of()
@template.admin.components.pageShell("Upload media | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, adminStylesheetPaths = adminStylesheetPaths, content = @`
    <main id="admin-content" tabindex="-1">
        <h1>Upload media</h1>
        <p><a href="/admin/media">Back to media</a></p>
        @if(page.hasErrors())
            @for(var error : page.globalErrors())
                <p role="alert">${error}</p>
            @endfor
            @template.admin.components.fieldErrors(page.fieldErrors())
        @endif
        <form method="post" enctype="multipart/form-data" action="/admin/media">
            @template.admin.components.csrfField(page.chrome().logoutForm())
            <label for="file">File</label>
            <input id="file" name="file" type="file" accept="image/jpeg,image/png,application/pdf" aria-invalid="${FieldErrors.has(page.fieldErrors(), "file") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "file") ? "error-file" : null}">
            <p>Allowed types: JPEG, PNG, PDF.</p>
            <button type="submit">Upload media</button>
        </form>
    </main>
`)
