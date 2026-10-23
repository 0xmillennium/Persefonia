@import dev.persefonia.webadmin.FieldErrors
@import dev.persefonia.webadmin.profile.AdminPersonalProfilePage
@param AdminPersonalProfilePage page
@param java.util.List<String> adminStylesheetPaths = java.util.List.of()
@template.admin.components.pageShell("Profile | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, adminStylesheetPaths = adminStylesheetPaths, content = @`
    <main id="admin-content" tabindex="-1">
        <h1>${page.onboarding() ? "Create profile" : "Edit profile"}</h1>
        <p>Default language: ${page.defaultLanguage()}</p>
        @if(page.successMessage() != null)
            <p>${page.successMessage()}</p>
        @endif
        @if(page.hasErrors())
            @for(var error : page.globalErrors())
                <p role="alert">${error}</p>
            @endfor
            @template.admin.components.fieldErrors(page.fieldErrors())
        @endif
        <form method="post" action="/admin/profile">
            @template.admin.components.csrfField(page.chrome().logoutForm())

            <label for="displayName">Display name</label>
            <input id="displayName" name="displayName" maxlength="160" value="${page.form().getDisplayName()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "displayName") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "displayName") ? "error-displayName" : null}">

            <div id="localizations" tabindex="-1" aria-describedby="${FieldErrors.has(page.fieldErrors(), "localizations") ? "error-localizations" : null}">
            <fieldset>
                <legend>TR</legend>
                <label><input type="checkbox" name="trEnabled" value="true" checked="${page.form().isTrEnabled()}" id="trEnabled" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trEnabled") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trEnabled") ? "error-trEnabled" : null}"> Enabled</label>

                <label for="trShortBio">Short bio</label>
                <textarea id="trShortBio" name="trShortBio" maxlength="600" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trShortBio") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trShortBio") ? "error-trShortBio" : null}">${page.form().getTrShortBio()}</textarea>

                <label for="trLongBio">Long bio</label>
                <textarea id="trLongBio" name="trLongBio" maxlength="5000" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trLongBio") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trLongBio") ? "error-trLongBio" : null}">${page.form().getTrLongBio()}</textarea>

                <label for="trLocationText">Location</label>
                <input id="trLocationText" name="trLocationText" maxlength="160" value="${page.form().getTrLocationText()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trLocationText") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trLocationText") ? "error-trLocationText" : null}">

                <label for="trTechnicalFocusAreas">Technical focus areas</label>
                <p>Name or Name | Optional description</p>
                <textarea id="trTechnicalFocusAreas" name="trTechnicalFocusAreas" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trTechnicalFocusAreas") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trTechnicalFocusAreas") ? "error-trTechnicalFocusAreas" : null}">${page.form().getTrTechnicalFocusAreas()}</textarea>

                <label for="trEducationSummaries">Education summaries</label>
                <p>Institution | Program or Institution | Program | Optional description</p>
                <textarea id="trEducationSummaries" name="trEducationSummaries" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trEducationSummaries") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trEducationSummaries") ? "error-trEducationSummaries" : null}">${page.form().getTrEducationSummaries()}</textarea>

                <label for="trCurrentFocusItems">Current focus items</label>
                <p>One item per line</p>
                <textarea id="trCurrentFocusItems" name="trCurrentFocusItems" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trCurrentFocusItems") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trCurrentFocusItems") ? "error-trCurrentFocusItems" : null}">${page.form().getTrCurrentFocusItems()}</textarea>
            </fieldset>

            <fieldset>
                <legend>EN</legend>
                <label><input type="checkbox" name="enEnabled" value="true" checked="${page.form().isEnEnabled()}" id="enEnabled" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enEnabled") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enEnabled") ? "error-enEnabled" : null}"> Enabled</label>

                <label for="enShortBio">Short bio</label>
                <textarea id="enShortBio" name="enShortBio" maxlength="600" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enShortBio") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enShortBio") ? "error-enShortBio" : null}">${page.form().getEnShortBio()}</textarea>

                <label for="enLongBio">Long bio</label>
                <textarea id="enLongBio" name="enLongBio" maxlength="5000" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enLongBio") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enLongBio") ? "error-enLongBio" : null}">${page.form().getEnLongBio()}</textarea>

                <label for="enLocationText">Location</label>
                <input id="enLocationText" name="enLocationText" maxlength="160" value="${page.form().getEnLocationText()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enLocationText") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enLocationText") ? "error-enLocationText" : null}">

                <label for="enTechnicalFocusAreas">Technical focus areas</label>
                <p>Name or Name | Optional description</p>
                <textarea id="enTechnicalFocusAreas" name="enTechnicalFocusAreas" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enTechnicalFocusAreas") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enTechnicalFocusAreas") ? "error-enTechnicalFocusAreas" : null}">${page.form().getEnTechnicalFocusAreas()}</textarea>

                <label for="enEducationSummaries">Education summaries</label>
                <p>Institution | Program or Institution | Program | Optional description</p>
                <textarea id="enEducationSummaries" name="enEducationSummaries" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enEducationSummaries") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enEducationSummaries") ? "error-enEducationSummaries" : null}">${page.form().getEnEducationSummaries()}</textarea>

                <label for="enCurrentFocusItems">Current focus items</label>
                <p>One item per line</p>
                <textarea id="enCurrentFocusItems" name="enCurrentFocusItems" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enCurrentFocusItems") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enCurrentFocusItems") ? "error-enCurrentFocusItems" : null}">${page.form().getEnCurrentFocusItems()}</textarea>
            </fieldset>

            </div>
            <label for="externalLinks">External links</label>
            <p>Label | https://example.com</p>
            <textarea id="externalLinks" name="externalLinks" aria-invalid="${FieldErrors.has(page.fieldErrors(), "externalLinks") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "externalLinks") ? "error-externalLinks" : null}">${page.form().getExternalLinks()}</textarea>

            <button type="submit">Save profile</button>
        </form>
    </main>
`)
