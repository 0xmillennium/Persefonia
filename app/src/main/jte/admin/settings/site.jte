@import dev.persefonia.webadmin.FieldErrors
@import dev.persefonia.webadmin.settings.AdminSiteSettingsPage
@param AdminSiteSettingsPage page
@param java.util.List<String> adminStylesheetPaths = java.util.List.of()
@template.admin.components.pageShell("Site settings | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, adminStylesheetPaths = adminStylesheetPaths, content = @`
    <main id="admin-content" tabindex="-1">
        <h1>Site settings</h1>
        @if(page.successMessage() != null)
            <p>${page.successMessage()}</p>
        @endif
        @if(page.hasErrors())
            @for(var error : page.globalErrors())
                <p role="alert">${error}</p>
            @endfor
            @template.admin.components.fieldErrors(page.fieldErrors())
        @endif
        <form method="post" action="/admin/settings/site">
            @template.admin.components.csrfField(page.chrome().logoutForm())
            <label for="siteName">Site name</label>
            <input id="siteName" name="siteName" maxlength="120" value="${page.form().getSiteName()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "siteName") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "siteName") ? "error-siteName" : null}">

            <label for="defaultLanguage">Default language</label>
            <select id="defaultLanguage" name="defaultLanguage" aria-invalid="${FieldErrors.has(page.fieldErrors(), "defaultLanguage") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "defaultLanguage") ? "error-defaultLanguage" : null}">
                <option value="TR" selected="${"TR".equals(page.form().getDefaultLanguage())}">TR</option>
                <option value="EN" selected="${"EN".equals(page.form().getDefaultLanguage())}">EN</option>
            </select>

            <fieldset id="supportedLanguages" tabindex="-1" aria-describedby="${FieldErrors.has(page.fieldErrors(), "supportedLanguages") ? "error-supportedLanguages" : null}">
                <legend>Supported languages</legend>
                <label><input type="checkbox" name="supportedTr" value="true" checked="${page.form().isSupportedTr()}" id="supportedTr" aria-invalid="${FieldErrors.has(page.fieldErrors(), "supportedTr") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "supportedTr") ? "error-supportedTr" : null}"> TR</label>
                <label><input type="checkbox" name="supportedEn" value="true" checked="${page.form().isSupportedEn()}" id="supportedEn" aria-invalid="${FieldErrors.has(page.fieldErrors(), "supportedEn") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "supportedEn") ? "error-supportedEn" : null}"> EN</label>
            </fieldset>

            <label for="titleSuffix">Title suffix</label>
            <input id="titleSuffix" name="titleSuffix" maxlength="120" value="${page.form().getTitleSuffix()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "titleSuffix") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "titleSuffix") ? "error-titleSuffix" : null}">

            <label for="defaultMetaDescription">Default meta description</label>
            <textarea id="defaultMetaDescription" name="defaultMetaDescription" maxlength="300" aria-invalid="${FieldErrors.has(page.fieldErrors(), "defaultMetaDescription") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "defaultMetaDescription") ? "error-defaultMetaDescription" : null}">${page.form().getDefaultMetaDescription()}</textarea>

            <label for="standardPublicTheme">Standard public theme</label>
            <p>LIGHT, DARK, and SYSTEM apply to standard public content pages. The Persefonia homepage keeps its editorial dark presentation.</p>
            <select id="standardPublicTheme" name="standardPublicTheme" aria-invalid="${FieldErrors.has(page.fieldErrors(), "standardPublicTheme") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "standardPublicTheme") ? "error-standardPublicTheme" : null}">
                <option value="SYSTEM" selected="${"SYSTEM".equals(page.form().getStandardPublicTheme())}">System</option>
                <option value="LIGHT" selected="${"LIGHT".equals(page.form().getStandardPublicTheme())}">Light</option>
                <option value="DARK" selected="${"DARK".equals(page.form().getStandardPublicTheme())}">Dark</option>
            </select>

            <fieldset>
                <legend>Homepage content promotion</legend>
                <label><input type="checkbox" name="promoteFeaturedProjects" value="true" checked="${page.form().isPromoteFeaturedProjects()}" id="promoteFeaturedProjects" aria-invalid="${FieldErrors.has(page.fieldErrors(), "promoteFeaturedProjects") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "promoteFeaturedProjects") ? "error-promoteFeaturedProjects" : null}"> Featured projects</label>
                <label><input type="checkbox" name="promoteLatestWriting" value="true" checked="${page.form().isPromoteLatestWriting()}" id="promoteLatestWriting" aria-invalid="${FieldErrors.has(page.fieldErrors(), "promoteLatestWriting") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "promoteLatestWriting") ? "error-promoteLatestWriting" : null}"> Latest writing</label>
                <label><input type="checkbox" name="promoteResearchHighlights" value="true" checked="${page.form().isPromoteResearchHighlights()}" id="promoteResearchHighlights" aria-invalid="${FieldErrors.has(page.fieldErrors(), "promoteResearchHighlights") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "promoteResearchHighlights") ? "error-promoteResearchHighlights" : null}"> Research highlights</label>
            </fieldset>

            <label for="featuredProjectLimit">Featured project limit</label>
            <input id="featuredProjectLimit" name="featuredProjectLimit" inputmode="numeric" value="${page.form().getFeaturedProjectLimit()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "featuredProjectLimit") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "featuredProjectLimit") ? "error-featuredProjectLimit" : null}">

            <label for="latestWritingLimit">Latest writing limit</label>
            <input id="latestWritingLimit" name="latestWritingLimit" inputmode="numeric" value="${page.form().getLatestWritingLimit()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "latestWritingLimit") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "latestWritingLimit") ? "error-latestWritingLimit" : null}">

            <label for="researchHighlightLimit">Research highlight limit</label>
            <input id="researchHighlightLimit" name="researchHighlightLimit" inputmode="numeric" value="${page.form().getResearchHighlightLimit()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "researchHighlightLimit") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "researchHighlightLimit") ? "error-researchHighlightLimit" : null}">

            <button type="submit">Save settings</button>
        </form>
    </main>
`)
