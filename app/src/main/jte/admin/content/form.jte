@import dev.persefonia.webadmin.FieldErrors
@import dev.persefonia.webadmin.content.AdminContentFormPage
@import dev.persefonia.contentpublishing.application.query.ContentTranslationSectionView
@param AdminContentFormPage page
@param ContentTranslationSectionView translationSection = null
@param String translationMessage = null
@param String translationError = null
@param java.util.List<String> adminStylesheetPaths = java.util.List.of()
@template.admin.components.pageShell(page.heading() + " | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, adminStylesheetPaths = adminStylesheetPaths, content = @`
<main id="admin-content" tabindex="-1">
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
            @template.admin.components.fieldErrors(page.fieldErrors())
        </section>
    @endif
    @if(page.create() || page.editable())
        <form method="post" action="${page.action()}">
            @template.admin.components.csrfField(page.chrome().logoutForm())
            <label for="type">Type</label>
            @if(page.create())
                <select id="type" name="type" aria-invalid="${FieldErrors.has(page.fieldErrors(), "type") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "type") ? "error-type" : null}">
                    <option value="">Choose type</option>
                    @for(String value : java.util.List.of("ARTICLE", "NOTE", "RESEARCH", "PAGE"))
                        <option value="${value}" selected="${value.equals(page.form().getType())}">${value}</option>
                    @endfor
                </select>
            @else
                <select id="type" name="type" disabled aria-invalid="${FieldErrors.has(page.fieldErrors(), "type") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "type") ? "error-type" : null}">
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
                <select id="language" name="language" aria-invalid="${FieldErrors.has(page.fieldErrors(), "language") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "language") ? "error-language" : null}">
                    <option value="">Choose language</option>
                    @for(String value : java.util.List.of("TR", "EN"))
                        <option value="${value}" selected="${value.equals(page.form().getLanguage())}">${value}</option>
                    @endfor
                </select>
            @else
                <select id="language" name="language" disabled aria-invalid="${FieldErrors.has(page.fieldErrors(), "language") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "language") ? "error-language" : null}">
                    @for(String value : java.util.List.of("TR", "EN"))
                        <option value="${value}" selected="${value.equals(page.form().getLanguage())}">${value}</option>
                    @endfor
                </select>
            @endif
            @if(!page.create())
                <input type="hidden" name="language" value="${page.form().getLanguage()}">
            @endif

            <label for="visibility">Visibility</label>
            <select id="visibility" name="visibility" aria-invalid="${FieldErrors.has(page.fieldErrors(), "visibility") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "visibility") ? "error-visibility" : null}">
                <option value="">Choose visibility</option>
                @for(String value : java.util.List.of("PUBLIC", "UNLISTED", "PRIVATE"))
                    <option value="${value}" selected="${value.equals(page.form().getVisibility())}">${value}</option>
                @endfor
            </select>

            @if(!page.create())
                <label for="slug">Slug</label>
                <input id="slug" name="slug" value="${page.form().getSlug()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "slug") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "slug") ? "error-slug" : null}">
                <label for="title">Title</label>
                <input id="title" name="title" maxlength="200" value="${page.form().getTitle()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "title") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "title") ? "error-title" : null}">
                <label for="summary">Summary</label>
                <textarea id="summary" name="summary" maxlength="500" aria-invalid="${FieldErrors.has(page.fieldErrors(), "summary") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "summary") ? "error-summary" : null}">${page.form().getSummary()}</textarea>
                <label for="markdownSource">Markdown source</label>
                <textarea id="markdownSource" name="markdownSource" rows="24" aria-invalid="${FieldErrors.has(page.fieldErrors(), "markdownSource") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "markdownSource") ? "error-markdownSource" : null}">${page.form().getMarkdownSource()}</textarea>
                <label for="metaTitle">SEO title</label>
                <input id="metaTitle" name="metaTitle" maxlength="200" value="${page.form().getMetaTitle()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "metaTitle") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "metaTitle") ? "error-metaTitle" : null}">
                <label for="metaDescription">SEO description</label>
                <textarea id="metaDescription" name="metaDescription" maxlength="500" aria-invalid="${FieldErrors.has(page.fieldErrors(), "metaDescription") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "metaDescription") ? "error-metaDescription" : null}">${page.form().getMetaDescription()}</textarea>
                <label for="canonicalPath">Canonical path</label>
                <input id="canonicalPath" name="canonicalPath" value="${page.form().getCanonicalPath()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "canonicalPath") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "canonicalPath") ? "error-canonicalPath" : null}">
                <label for="ogTitle">Open Graph title</label>
                <input id="ogTitle" name="ogTitle" maxlength="200" value="${page.form().getOgTitle()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "ogTitle") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "ogTitle") ? "error-ogTitle" : null}">
                <label for="ogDescription">Open Graph description</label>
                <textarea id="ogDescription" name="ogDescription" maxlength="500" aria-invalid="${FieldErrors.has(page.fieldErrors(), "ogDescription") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "ogDescription") ? "error-ogDescription" : null}">${page.form().getOgDescription()}</textarea>
                <label for="ogImageAssetId">Gallery cover / Open Graph image asset ID</label>
                <p>Attach an image from the media library. Only processed public image variants appear in the homepage gallery.</p>
                <input id="ogImageAssetId" name="ogImageAssetId" value="${page.form().getOgImageAssetId()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "ogImageAssetId") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "ogImageAssetId") ? "error-ogImageAssetId" : null}">
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
            <form method="post" action="/admin/content/${page.tagAssignment().contentId().value().toString()}/tags">
                @template.admin.components.csrfField(page.chrome().logoutForm())
                <fieldset id="tagId" tabindex="-1" aria-invalid="${page.hasTagAssignmentErrors() ? "true" : null}" aria-describedby="${page.hasTagAssignmentErrors() ? "content-tag-errors" : null}">
                    <legend>Tags</legend>
                    @if(page.hasTagAssignmentErrors())
                        <div id="content-tag-errors" role="alert">
                            @for(var error : page.tagAssignmentErrors())
                                <p>${error}</p>
                            @endfor
                        </div>
                    @endif
                    @if(page.tagAssignment().assignableTags().isEmpty() && page.tagAssignment().assignedTags().stream().noneMatch(tag -> tag.archived()))
                        <p>No assignable tags are available.</p>
                    @else
                        @for(var tag : page.tagAssignment().assignableTags())
                            <label>
                                <input type="checkbox"
                                       name="tagId"
                                       value="${tag.id().value().toString()}"
                                       checked="${page.selectedTagIds().contains(tag.id().value().toString())}" id="content-tag-${tag.id().value().toString()}">
                                ${tag.name()}
                            </label>
                        @endfor
                        @for(var tag : page.tagAssignment().assignedTags())
                            @if(tag.archived())
                                <label>
                                    <input type="checkbox"
                                           name="tagId"
                                           value="${tag.id().value().toString()}"
                                           checked="${page.selectedTagIds().contains(tag.id().value().toString())}" id="content-tag-${tag.id().value().toString()}">
                                    ${tag.name()} <strong>ARCHIVED</strong>
                                </label>
                            @endif
                        @endfor
                    @endif
                </fieldset>
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
                        <select id="translationCandidate" name="contentItemId" aria-invalid="${FieldErrors.has(page.fieldErrors(), "contentItemId") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "contentItemId") ? "error-contentItemId" : null}">
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
`)
