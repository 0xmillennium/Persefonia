@import dev.persefonia.webadmin.FieldErrors
@import dev.persefonia.webadmin.cv.AdminCvPage
@param AdminCvPage page
@param java.util.List<String> adminStylesheetPaths = java.util.List.of()
@template.admin.components.pageShell("CV | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, adminStylesheetPaths = adminStylesheetPaths, content = @`
    <main id="admin-content" tabindex="-1">
        <h1>CV</h1>
        <p><a href="/admin/media">Manage PDF assets</a></p>
        @if(page.successMessage() != null)
            <p role="status">${page.successMessage()}</p>
        @endif
        @if(page.hasErrors())
            @for(var error : page.globalErrors())
                <p role="alert">${error}</p>
            @endfor
            @template.admin.components.fieldErrors(page.fieldErrors())
        @endif
        @if(page.data().candidates().isEmpty())
            <p>No public PDF assets are available.</p>
        @endif
        <form method="post" action="/admin/cv">
            @template.admin.components.csrfField(page.chrome().logoutForm())

            @if(page.data().supportedLanguages().contains("TR"))
                <fieldset>
                    <legend>TR</legend>
                    <label for="trAssetId">PDF asset</label>
                    <select id="trAssetId" name="trAssetId" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trAssetId") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trAssetId") ? "error-trAssetId" : null}">
                        <option value="" selected="${page.form().getTrAssetId().isBlank()}">No active CV</option>
                        @for(var candidate : page.data().candidates())
                            <option value="${candidate.mediaAssetId().toString()}" selected="${candidate.mediaAssetId().toString().equals(page.form().getTrAssetId())}">${candidate.originalFilename()} (${candidate.contentType()}, ${Long.toString(candidate.sizeBytes())} bytes)</option>
                        @endfor
                    </select>

                    <label for="trDisplayLabel">Display label</label>
                    <input id="trDisplayLabel" name="trDisplayLabel" maxlength="160" value="${page.form().getTrDisplayLabel()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trDisplayLabel") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trDisplayLabel") ? "error-trDisplayLabel" : null}">
                </fieldset>
            @endif

            @if(page.data().supportedLanguages().contains("EN"))
                <fieldset>
                    <legend>EN</legend>
                    <label for="enAssetId">PDF asset</label>
                    <select id="enAssetId" name="enAssetId" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enAssetId") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enAssetId") ? "error-enAssetId" : null}">
                        <option value="" selected="${page.form().getEnAssetId().isBlank()}">No active CV</option>
                        @for(var candidate : page.data().candidates())
                            <option value="${candidate.mediaAssetId().toString()}" selected="${candidate.mediaAssetId().toString().equals(page.form().getEnAssetId())}">${candidate.originalFilename()} (${candidate.contentType()}, ${Long.toString(candidate.sizeBytes())} bytes)</option>
                        @endfor
                    </select>

                    <label for="enDisplayLabel">Display label</label>
                    <input id="enDisplayLabel" name="enDisplayLabel" maxlength="160" value="${page.form().getEnDisplayLabel()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enDisplayLabel") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enDisplayLabel") ? "error-enDisplayLabel" : null}">
                </fieldset>
            @endif

            <button type="submit">Save CV</button>
        </form>
    </main>
`)
