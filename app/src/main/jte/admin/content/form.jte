@import dev.persefonia.webadmin.content.AdminContentFormPage
@param AdminContentFormPage page
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex,nofollow,noarchive">
    <title>${page.heading()} | Persefonia Admin</title>
</head>
<body>
<header>
    <p>Signed in as <strong>${page.chrome().admin().displayName()}</strong></p>
    <form method="post" action="${page.chrome().logoutForm().action()}">
        <input type="hidden" name="${page.chrome().logoutForm().csrfParameterName()}" value="${page.chrome().logoutForm().csrfToken()}">
        <button type="submit">Logout</button>
    </form>
</header>
<nav aria-label="Admin navigation">
    <ul>
        @for(var item : page.chrome().navigation())
            <li>
                @if(item.disabled())
                    <span aria-disabled="true">${item.label()}</span>
                @else
                    <a href="${item.href()}" aria-current="${item.active() ? "page" : null}">${item.label()}</a>
                @endif
            </li>
        @endfor
    </ul>
</nav>
<main>
    <h1>${page.heading()}</h1>
    <p><a href="/admin/content">Back to content</a></p>
    @if(page.successMessage() != null)
        <p role="status">${page.successMessage()}</p>
    @endif
    @if(page.status() != null)
        <p>Status: <strong>${page.status()}</strong></p>
    @endif
    @if(page.previewLink() != null)
        <p><a href="${page.previewLink()}">Preview persisted content</a></p>
    @endif
    @if(page.hasErrors())
        <section aria-label="Errors">
            @for(var error : page.globalErrors())
                <p role="alert">${error}</p>
            @endfor
            <ul>
                @for(var error : page.fieldErrors())
                    <li><a href="#${error.field()}">${error.field()}: ${error.message()}</a></li>
                @endfor
            </ul>
        </section>
    @endif
    <form method="post" action="${page.action()}">
        <input type="hidden" name="${page.chrome().logoutForm().csrfParameterName()}" value="${page.chrome().logoutForm().csrfToken()}">
        <label for="type">Type</label>
        @if(page.create())
            <select id="type" name="type">
                <option value="">Choose type</option>
                @for(String value : java.util.List.of("ARTICLE", "NOTE", "RESEARCH", "PAGE"))
                    <option value="${value}" selected="${value.equals(page.form().getType())}">${value}</option>
                @endfor
            </select>
        @else
            <select id="type" name="type" disabled>
                @for(String value : java.util.List.of("ARTICLE", "NOTE", "RESEARCH", "PAGE"))
                    <option value="${value}" selected="${value.equals(page.form().getType())}">${value}</option>
                @endfor
            </select>
        @endif
        @if(!page.create())
            <input type="hidden" name="type" value="${page.form().getType()}">
        @endif

        <label for="language">Language</label>
        @if(page.create())
            <select id="language" name="language">
                <option value="">Choose language</option>
                @for(String value : java.util.List.of("TR", "EN"))
                    <option value="${value}" selected="${value.equals(page.form().getLanguage())}">${value}</option>
                @endfor
            </select>
        @else
            <select id="language" name="language" disabled>
                @for(String value : java.util.List.of("TR", "EN"))
                    <option value="${value}" selected="${value.equals(page.form().getLanguage())}">${value}</option>
                @endfor
            </select>
        @endif
        @if(!page.create())
            <input type="hidden" name="language" value="${page.form().getLanguage()}">
        @endif

        <label for="visibility">Visibility</label>
        <select id="visibility" name="visibility">
            <option value="">Choose visibility</option>
            @for(String value : java.util.List.of("PUBLIC", "UNLISTED", "PRIVATE"))
                <option value="${value}" selected="${value.equals(page.form().getVisibility())}">${value}</option>
            @endfor
        </select>

        @if(!page.create())
            <label for="slug">Slug</label>
            <input id="slug" name="slug" value="${page.form().getSlug()}">
            <label for="title">Title</label>
            <input id="title" name="title" maxlength="200" value="${page.form().getTitle()}">
            <label for="summary">Summary</label>
            <textarea id="summary" name="summary" maxlength="500">${page.form().getSummary()}</textarea>
            <label for="markdownSource">Markdown source</label>
            <textarea id="markdownSource" name="markdownSource" rows="24">${page.form().getMarkdownSource()}</textarea>
            <label for="metaTitle">SEO title</label>
            <input id="metaTitle" name="metaTitle" maxlength="200" value="${page.form().getMetaTitle()}">
            <label for="metaDescription">SEO description</label>
            <textarea id="metaDescription" name="metaDescription" maxlength="500">${page.form().getMetaDescription()}</textarea>
            <label for="canonicalPath">Canonical path</label>
            <input id="canonicalPath" name="canonicalPath" value="${page.form().getCanonicalPath()}">
            <label for="ogTitle">Open Graph title</label>
            <input id="ogTitle" name="ogTitle" maxlength="200" value="${page.form().getOgTitle()}">
            <label for="ogDescription">Open Graph description</label>
            <textarea id="ogDescription" name="ogDescription" maxlength="500">${page.form().getOgDescription()}</textarea>
            <label for="ogImageAssetId">Open Graph image asset ID</label>
            <input id="ogImageAssetId" name="ogImageAssetId" value="${page.form().getOgImageAssetId()}">
        @endif
        <button type="submit">${page.create() ? "Create draft" : "Save changes"}</button>
    </form>
</main>
</body>
</html>
