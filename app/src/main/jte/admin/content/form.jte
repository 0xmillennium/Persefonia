@import dev.persefonia.webadmin.content.AdminContentFormPage
@import dev.persefonia.contentpublishing.application.query.ContentTranslationSectionView
@param AdminContentFormPage page
@param ContentTranslationSectionView translationSection = null
@param String translationMessage = null
@param String translationError = null
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex,nofollow,noarchive">
    <title>${page.heading()} | Persefonia Admin</title>
</head>
<body>
@template.admin.components.adminHeader(page.chrome().admin(), page.chrome().logoutForm(), false)
@template.admin.components.adminNavigation(page.chrome().navigation())
<main>
    <h1>${page.heading()}</h1>
    <p><a href="/admin/content">Back to content</a></p>
    @if(page.successMessage() != null)
        <p role="status">${page.successMessage()}</p>
    @endif
    @if(page.status() != null)
        <p>Status: <strong>${page.status()}</strong></p>
    @endif
    @if(page.readOnlyMessage() != null)
        <p>${page.readOnlyMessage()}</p>
    @endif
    @if(page.previewLink() != null)
        <p><a href="${page.previewLink()}">Preview persisted content</a></p>
    @endif
    @if(page.revisionsLink() != null)
        <p><a href="${page.revisionsLink()}">Revision history</a></p>
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
    @if(page.create() || page.editable())
        <form method="post" action="${page.action()}">
            @template.admin.components.csrfField(page.chrome().logoutForm())
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
    @endif
    @if(page.tagAssignment() != null)
        <section aria-label="Tag assignments">
            <h2>Tags</h2>
            @if(page.tagAssignmentSuccessMessage() != null)
                <p role="status">${page.tagAssignmentSuccessMessage()}</p>
            @endif
            @for(var error : page.tagAssignmentErrors())
                <p role="alert">${error}</p>
            @endfor
            <form method="post" action="/admin/content/${page.tagAssignment().contentId().value().toString()}/tags">
                @template.admin.components.csrfField(page.chrome().logoutForm())
                @if(page.tagAssignment().assignableTags().isEmpty() && page.tagAssignment().assignedTags().stream().noneMatch(tag -> tag.archived()))
                    <p>No assignable tags are available.</p>
                @else
                    @for(var tag : page.tagAssignment().assignableTags())
                        <label>
                            <input type="checkbox"
                                   name="tagId"
                                   value="${tag.id().value().toString()}"
                                   checked="${page.selectedTagIds().contains(tag.id().value().toString())}">
                            ${tag.name()}
                        </label>
                    @endfor
                    @for(var tag : page.tagAssignment().assignedTags())
                        @if(tag.archived())
                            <label>
                                <input type="checkbox"
                                       name="tagId"
                                       value="${tag.id().value().toString()}"
                                       checked="${page.selectedTagIds().contains(tag.id().value().toString())}">
                                ${tag.name()} <strong>ARCHIVED</strong>
                            </label>
                        @endif
                    @endfor
                @endif
                <button type="submit">Save tags</button>
            </form>
        </section>
    @endif
    @if(translationSection != null)
        <section aria-label="Translations">
            <h2>Translations</h2>
            @if(translationMessage != null)
                <p role="status">${translationMessage}</p>
            @endif
            @if(translationError != null)
                <p role="alert">${translationError}</p>
            @endif
            @if(translationSection.hasGroup())
                <ul>
                    @for(var entry : translationSection.group().get().entries())
                        <li>
                            <strong>${entry.language().name()}</strong>
                            <a href="/admin/content/${entry.contentItemId().value().toString()}/edit">${entry.title().orElse("Untitled")}</a>
                            @if(translationSection.group().get().canRemoveEntries())
                                <form method="post" action="/admin/translation-groups/${translationSection.group().get().groupId().value().toString()}/entries/${entry.entryId().value().toString()}/remove">
                                    @template.admin.components.csrfField(page.chrome().logoutForm())
                                    <input type="hidden" name="returnContentId" value="${translationSection.contentItemId().value().toString()}">
                                    <button type="submit">Remove</button>
                                </form>
                            @endif
                        </li>
                    @endfor
                </ul>
                @if(translationSection.candidates().isEmpty())
                    <p>No content items are available to add as translations.</p>
                @else
                    <form method="post" action="/admin/translation-groups/${translationSection.group().get().groupId().value().toString()}/entries">
                        @template.admin.components.csrfField(page.chrome().logoutForm())
                        <input type="hidden" name="returnContentId" value="${translationSection.contentItemId().value().toString()}">
                        <label for="translationCandidate">Add translation</label>
                        <select id="translationCandidate" name="contentItemId">
                            @for(var candidate : translationSection.candidates())
                                <option value="${candidate.contentItemId().value().toString()}">${candidate.language().name()} — ${candidate.title().orElse("Untitled")}</option>
                            @endfor
                        </select>
                        <button type="submit">Add translation</button>
                    </form>
                @endif
            @else
                <p>This content item is not part of a translation group.</p>
                <form method="post" action="/admin/content/${translationSection.contentItemId().value().toString()}/translation-group">
                    @template.admin.components.csrfField(page.chrome().logoutForm())
                    <button type="submit">Create translation group</button>
                </form>
            @endif
        </section>
    @endif
    @if(page.lifecycleActions().hasActions())
        <section aria-label="Lifecycle actions">
            <h2>Lifecycle actions</h2>
            @if(page.lifecycleActions().canPublish())
                <form method="post" action="${page.lifecycleActions().publishAction()}">
                    @template.admin.components.csrfField(page.chrome().logoutForm())
                    <button type="submit">Publish</button>
                </form>
            @endif
            @if(page.lifecycleActions().canUnpublish())
                <form method="post" action="${page.lifecycleActions().unpublishAction()}">
                    @template.admin.components.csrfField(page.chrome().logoutForm())
                    <button type="submit">Unpublish</button>
                </form>
            @endif
            @if(page.lifecycleActions().canArchive())
                <form method="post" action="${page.lifecycleActions().archiveAction()}">
                    @template.admin.components.csrfField(page.chrome().logoutForm())
                    <button type="submit">Archive</button>
                </form>
            @endif
        </section>
    @endif
</main>
</body>
</html>
