@import dev.persefonia.webadmin.FieldErrors
@import dev.persefonia.webadmin.series.AdminSeriesFormPage
@param AdminSeriesFormPage page
@param java.util.List<String> adminStylesheetPaths = java.util.List.of()
@template.admin.components.pageShell("Edit series | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, adminStylesheetPaths = adminStylesheetPaths, content = @`
    <main id="admin-content" tabindex="-1">
        <h1 id="series" tabindex="-1" aria-describedby="${page.globalErrors().isEmpty() ? null : "series-errors"}">${page.heading()}</h1>
        <p><a href="/admin/series">Back to series</a></p>
        @if(page.successMessage() != null)
            <p role="status">${page.successMessage()}</p>
        @endif
        @if(page.hasErrors())
            @if(!page.globalErrors().isEmpty())
                <section id="series-errors" role="alert" tabindex="-1" autofocus aria-label="Series errors">
                    @for(var error : page.globalErrors())
                        <p><a href="#series">${error}</a></p>
                    @endfor
                </section>
            @endif
            @template.admin.components.fieldErrors(page.fieldErrors())
        @endif
        <p>Status: <strong>${page.series().status().name()}</strong></p>
        <p>Language: <strong>${page.series().language().name()}</strong></p>
        @if("ACTIVE".equals(page.series().status().name()))
            <form method="post" action="${page.action()}">
                @template.admin.components.csrfField(page.chrome().logoutForm())
                <input type="hidden" name="language" value="${page.form().getLanguage()}">
                <label for="title">Title</label>
                <input id="title" name="title" maxlength="120" value="${page.form().getTitle()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "title") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "title") ? "error-title" : null}">
                <label for="slug">Slug</label>
                <input id="slug" name="slug" maxlength="100" value="${page.form().getSlug()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "slug") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "slug") ? "error-slug" : null}">
                <label for="description">Description</label>
                <textarea id="description" name="description" maxlength="500" aria-invalid="${FieldErrors.has(page.fieldErrors(), "description") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "description") ? "error-description" : null}">${page.form().getDescription()}</textarea>
                <button type="submit">Save changes</button>
            </form>
        @else
            <p>Archived series are read-only.</p>
        @endif

        <h2 id="entries" tabindex="-1">Entries</h2>
        <div id="entry" tabindex="-1"></div>
        @if(page.series().entries().isEmpty())
            <p>No entries yet.</p>
        @else
            @if("ACTIVE".equals(page.series().status().name()))
                <form method="post" action="/admin/series/${page.series().id().value().toString()}/entries/reorder">
                    @template.admin.components.csrfField(page.chrome().logoutForm())
                    <div class="admin-table-scroll" role="region" aria-label="Scrollable table" tabindex="0"><table>
                        <thead><tr><th>Position</th><th>Title</th><th>Type</th><th>Status</th><th>Remove</th></tr></thead>
                        <tbody>
                        @for(var entry : page.series().entries())
                            <tr>
                                <td>
                                    <input type="hidden" name="entryIds" value="${entry.id().value().toString()}">
                                    <input name="positions" type="number" min="1" max="${page.series().entries().size()}" value="${entry.position()}" id="position-${entry.id().value().toString()}" aria-label="Position for ${entry.title().orElse("entry")}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "positions") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "positions") ? "error-positions" : null}">
                                </td>
                                <td>${entry.title().orElse(entry.contentItemId().value().toString())}</td>
                                <td>${entry.contentType().name()}</td>
                                <td>${entry.status().name()}</td>
                                <td>
                                    <button type="submit" form="remove-${entry.id().value().toString()}">Remove</button>
                                </td>
                            </tr>
                        @endfor
                        </tbody>
                    </table></div>
                    <button type="submit">Save order</button>
                </form>
                @for(var entry : page.series().entries())
                    <form id="remove-${entry.id().value().toString()}" method="post" action="/admin/series/${page.series().id().value().toString()}/entries/${entry.id().value().toString()}/remove">
                        @template.admin.components.csrfField(page.chrome().logoutForm())
                    </form>
                @endfor
            @else
                <div class="admin-table-scroll" role="region" aria-label="Scrollable table" tabindex="0"><table>
                    <thead><tr><th>Position</th><th>Title</th><th>Type</th><th>Status</th></tr></thead>
                    <tbody>
                    @for(var entry : page.series().entries())
                        <tr>
                            <td>${entry.position()}</td>
                            <td>${entry.title().orElse(entry.contentItemId().value().toString())}</td>
                            <td>${entry.contentType().name()}</td>
                            <td>${entry.status().name()}</td>
                        </tr>
                    @endfor
                    </tbody>
                </table></div>
            @endif
        @endif

        @if("ACTIVE".equals(page.series().status().name()))
            <h2>Add entry</h2>
            @if(page.series().candidates().isEmpty())
                <p>No candidate content items.</p>
            @else
                <form method="post" action="/admin/series/${page.series().id().value().toString()}/entries">
                    @template.admin.components.csrfField(page.chrome().logoutForm())
                    <label for="contentItemId">Content item</label>
                    <select id="contentItemId" name="contentItemId" aria-invalid="${FieldErrors.has(page.fieldErrors(), "contentItemId") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "contentItemId") ? "error-contentItemId" : null}">
                        @for(var candidate : page.series().candidates())
                            <option value="${candidate.contentItemId().value().toString()}">${candidate.title().orElse(candidate.contentItemId().value().toString())} (${candidate.contentType().name()}, ${candidate.status().name()})</option>
                        @endfor
                    </select>
                    <button type="submit">Add entry</button>
                </form>
            @endif
        @endif

        @if(page.archiveAction() != null)
            <form method="post" action="${page.archiveAction()}">
                @template.admin.components.csrfField(page.chrome().logoutForm())
                <button type="submit">Archive</button>
            </form>
        @endif
    </main>
`)
