@import dev.persefonia.webadmin.settings.AdminSiteSettingsPage
@param AdminSiteSettingsPage page
@template.admin.components.pageShell("Site settings | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>Site settings</h1>
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
        <form method="post" action="/admin/settings/site">
            @template.admin.components.csrfField(page.chrome().logoutForm())
            <label for="siteName">Site name</label>
            <input id="siteName" name="siteName" maxlength="120" value="${page.form().getSiteName()}">

            <label for="defaultLanguage">Default language</label>
            <select id="defaultLanguage" name="defaultLanguage">
                <option value="TR" selected="${"TR".equals(page.form().getDefaultLanguage())}">TR</option>
                <option value="EN" selected="${"EN".equals(page.form().getDefaultLanguage())}">EN</option>
            </select>

            <fieldset>
                <legend>Supported languages</legend>
                <label><input type="checkbox" name="supportedTr" value="true" checked="${page.form().isSupportedTr()}"> TR</label>
                <label><input type="checkbox" name="supportedEn" value="true" checked="${page.form().isSupportedEn()}"> EN</label>
            </fieldset>

            <label for="titleSuffix">Title suffix</label>
            <input id="titleSuffix" name="titleSuffix" maxlength="120" value="${page.form().getTitleSuffix()}">

            <label for="defaultMetaDescription">Default meta description</label>
            <textarea id="defaultMetaDescription" name="defaultMetaDescription" maxlength="300">${page.form().getDefaultMetaDescription()}</textarea>

            <label for="standardPublicTheme">Standard public theme</label>
            <p>LIGHT, DARK, and SYSTEM apply to standard public content pages. The Persefonia homepage keeps its editorial dark presentation.</p>
            <select id="standardPublicTheme" name="standardPublicTheme">
                <option value="SYSTEM" selected="${"SYSTEM".equals(page.form().getStandardPublicTheme())}">System</option>
                <option value="LIGHT" selected="${"LIGHT".equals(page.form().getStandardPublicTheme())}">Light</option>
                <option value="DARK" selected="${"DARK".equals(page.form().getStandardPublicTheme())}">Dark</option>
            </select>

            <fieldset>
                <legend>Homepage content promotion</legend>
                <label><input type="checkbox" name="promoteFeaturedProjects" value="true" checked="${page.form().isPromoteFeaturedProjects()}"> Featured projects</label>
                <label><input type="checkbox" name="promoteLatestWriting" value="true" checked="${page.form().isPromoteLatestWriting()}"> Latest writing</label>
                <label><input type="checkbox" name="promoteResearchHighlights" value="true" checked="${page.form().isPromoteResearchHighlights()}"> Research highlights</label>
            </fieldset>

            <label for="featuredProjectLimit">Featured project limit</label>
            <input id="featuredProjectLimit" name="featuredProjectLimit" inputmode="numeric" value="${page.form().getFeaturedProjectLimit()}">

            <label for="latestWritingLimit">Latest writing limit</label>
            <input id="latestWritingLimit" name="latestWritingLimit" inputmode="numeric" value="${page.form().getLatestWritingLimit()}">

            <label for="researchHighlightLimit">Research highlight limit</label>
            <input id="researchHighlightLimit" name="researchHighlightLimit" inputmode="numeric" value="${page.form().getResearchHighlightLimit()}">

            <button type="submit">Save settings</button>
        </form>
    </main>
`)
