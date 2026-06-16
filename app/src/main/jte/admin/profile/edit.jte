@import dev.persefonia.webadmin.profile.AdminPersonalProfilePage
@param AdminPersonalProfilePage page
@template.admin.components.pageShell("Profile | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>${page.onboarding() ? "Create profile" : "Edit profile"}</h1>
        <p>Default language: ${page.defaultLanguage()}</p>
        @if(page.successMessage() != null)
            <p>${page.successMessage()}</p>
        @endif
        @if(page.hasErrors())
            @for(var error : page.globalErrors())
                <p role="alert">${error}</p>
            @endfor
            @for(var error : page.fieldErrors())
                <p role="alert">${error.field()}: ${error.message()}</p>
            @endfor
        @endif
        <form method="post" action="/admin/profile">
            @template.admin.components.csrfField(page.chrome().logoutForm())

            <label for="displayName">Display name</label>
            <input id="displayName" name="displayName" maxlength="160" value="${page.form().getDisplayName()}">

            <fieldset>
                <legend>TR</legend>
                <label><input type="checkbox" name="trEnabled" value="true" checked="${page.form().isTrEnabled()}"> Enabled</label>

                <label for="trShortBio">Short bio</label>
                <textarea id="trShortBio" name="trShortBio" maxlength="600">${page.form().getTrShortBio()}</textarea>

                <label for="trLongBio">Long bio</label>
                <textarea id="trLongBio" name="trLongBio" maxlength="5000">${page.form().getTrLongBio()}</textarea>

                <label for="trLocationText">Location</label>
                <input id="trLocationText" name="trLocationText" maxlength="160" value="${page.form().getTrLocationText()}">

                <label for="trTechnicalFocusAreas">Technical focus areas</label>
                <p>Name or Name | Optional description</p>
                <textarea id="trTechnicalFocusAreas" name="trTechnicalFocusAreas">${page.form().getTrTechnicalFocusAreas()}</textarea>

                <label for="trEducationSummaries">Education summaries</label>
                <p>Institution | Program or Institution | Program | Optional description</p>
                <textarea id="trEducationSummaries" name="trEducationSummaries">${page.form().getTrEducationSummaries()}</textarea>

                <label for="trCurrentFocusItems">Current focus items</label>
                <p>One item per line</p>
                <textarea id="trCurrentFocusItems" name="trCurrentFocusItems">${page.form().getTrCurrentFocusItems()}</textarea>
            </fieldset>

            <fieldset>
                <legend>EN</legend>
                <label><input type="checkbox" name="enEnabled" value="true" checked="${page.form().isEnEnabled()}"> Enabled</label>

                <label for="enShortBio">Short bio</label>
                <textarea id="enShortBio" name="enShortBio" maxlength="600">${page.form().getEnShortBio()}</textarea>

                <label for="enLongBio">Long bio</label>
                <textarea id="enLongBio" name="enLongBio" maxlength="5000">${page.form().getEnLongBio()}</textarea>

                <label for="enLocationText">Location</label>
                <input id="enLocationText" name="enLocationText" maxlength="160" value="${page.form().getEnLocationText()}">

                <label for="enTechnicalFocusAreas">Technical focus areas</label>
                <p>Name or Name | Optional description</p>
                <textarea id="enTechnicalFocusAreas" name="enTechnicalFocusAreas">${page.form().getEnTechnicalFocusAreas()}</textarea>

                <label for="enEducationSummaries">Education summaries</label>
                <p>Institution | Program or Institution | Program | Optional description</p>
                <textarea id="enEducationSummaries" name="enEducationSummaries">${page.form().getEnEducationSummaries()}</textarea>

                <label for="enCurrentFocusItems">Current focus items</label>
                <p>One item per line</p>
                <textarea id="enCurrentFocusItems" name="enCurrentFocusItems">${page.form().getEnCurrentFocusItems()}</textarea>
            </fieldset>

            <label for="externalLinks">External links</label>
            <p>Label | https://example.com</p>
            <textarea id="externalLinks" name="externalLinks">${page.form().getExternalLinks()}</textarea>

            <button type="submit">Save profile</button>
        </form>
    </main>
`)
