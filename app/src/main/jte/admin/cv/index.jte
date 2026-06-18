@import dev.persefonia.webadmin.cv.AdminCvPage
@param AdminCvPage page
@template.admin.components.pageShell("CV | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>CV</h1>
        <p><a href="/admin/media">Manage PDF assets</a></p>
        @if(page.successMessage() != null)
            <p role="status">${page.successMessage()}</p>
        @endif
        @if(page.hasErrors())
            @for(var error : page.globalErrors())
                <p role="alert">${error}</p>
            @endfor
            @for(var error : page.fieldErrors())
                <p role="alert">${error.field()}: ${error.message()}</p>
            @endfor
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
                    <select id="trAssetId" name="trAssetId">
                        <option value="" selected="${page.form().getTrAssetId().isBlank()}">No active CV</option>
                        @for(var candidate : page.data().candidates())
                            <option value="${candidate.mediaAssetId().toString()}" selected="${candidate.mediaAssetId().toString().equals(page.form().getTrAssetId())}">${candidate.originalFilename()} (${candidate.contentType()}, ${Long.toString(candidate.sizeBytes())} bytes)</option>
                        @endfor
                    </select>

                    <label for="trDisplayLabel">Display label</label>
                    <input id="trDisplayLabel" name="trDisplayLabel" maxlength="160" value="${page.form().getTrDisplayLabel()}">
                </fieldset>
            @endif

            @if(page.data().supportedLanguages().contains("EN"))
                <fieldset>
                    <legend>EN</legend>
                    <label for="enAssetId">PDF asset</label>
                    <select id="enAssetId" name="enAssetId">
                        <option value="" selected="${page.form().getEnAssetId().isBlank()}">No active CV</option>
                        @for(var candidate : page.data().candidates())
                            <option value="${candidate.mediaAssetId().toString()}" selected="${candidate.mediaAssetId().toString().equals(page.form().getEnAssetId())}">${candidate.originalFilename()} (${candidate.contentType()}, ${Long.toString(candidate.sizeBytes())} bytes)</option>
                        @endfor
                    </select>

                    <label for="enDisplayLabel">Display label</label>
                    <input id="enDisplayLabel" name="enDisplayLabel" maxlength="160" value="${page.form().getEnDisplayLabel()}">
                </fieldset>
            @endif

            <button type="submit">Save CV</button>
        </form>
    </main>
`)
