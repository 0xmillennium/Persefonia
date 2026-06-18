@import dev.persefonia.webadmin.media.AdminMediaUploadPage
@param AdminMediaUploadPage page
@template.admin.components.pageShell("Upload media | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>Upload media</h1>
        <p><a href="/admin/media">Back to media</a></p>
        @if(page.hasErrors())
            @for(var error : page.globalErrors())
                <p role="alert">${error}</p>
            @endfor
            @for(var error : page.fieldErrors())
                <p role="alert">${error.field()}: ${error.message()}</p>
            @endfor
        @endif
        <form method="post" enctype="multipart/form-data" action="/admin/media">
            @template.admin.components.csrfField(page.chrome().logoutForm())
            <label for="file">File</label>
            <input id="file" name="file" type="file" accept="image/jpeg,image/png,application/pdf">
            <p>Allowed types: JPEG, PNG, PDF.</p>
            <button type="submit">Upload media</button>
        </form>
    </main>
`)
