@import dev.persefonia.webadmin.series.AdminSeriesFormPage
@param AdminSeriesFormPage page
@template.admin.components.pageShell("Edit series | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>${page.heading()}</h1>
        <p><a href="/admin/series">Back to series</a></p>
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
        <p>Status: <strong>${page.series().status().name()}</strong></p>
        <p>Language: <strong>${page.series().language().name()}</strong></p>
        @if("ACTIVE".equals(page.series().status().name()))
            <form method="post" action="${page.action()}">
                @template.admin.components.csrfField(page.chrome().logoutForm())
                <input type="hidden" name="language" value="${page.form().getLanguage()}">
                <label for="title">Title</label>
                <input id="title" name="title" maxlength="120" value="${page.form().getTitle()}">
                <label for="slug">Slug</label>
                <input id="slug" name="slug" maxlength="100" value="${page.form().getSlug()}">
                <label for="description">Description</label>
                <textarea id="description" name="description" maxlength="500">${page.form().getDescription()}</textarea>
                <button type="submit">Save changes</button>
            </form>
        @else
            <p>Archived series are read-only.</p>
        @endif

        <h2>Entries</h2>
        @if(page.series().entries().isEmpty())
            <p>No entries yet.</p>
        @else
            @if("ACTIVE".equals(page.series().status().name()))
                <form method="post" action="/admin/series/${page.series().id().value().toString()}/entries/reorder">
                    @template.admin.components.csrfField(page.chrome().logoutForm())
                    <table>
                        <thead><tr><th>Position</th><th>Title</th><th>Type</th><th>Status</th><th>Remove</th></tr></thead>
                        <tbody>
                        @for(var entry : page.series().entries())
                            <tr>
                                <td>
                                    <input type="hidden" name="entryIds" value="${entry.id().value().toString()}">
                                    <input name="positions" type="number" min="1" max="${page.series().entries().size()}" value="${entry.position()}">
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
                    </table>
                    <button type="submit">Save order</button>
                </form>
                @for(var entry : page.series().entries())
                    <form id="remove-${entry.id().value().toString()}" method="post" action="/admin/series/${page.series().id().value().toString()}/entries/${entry.id().value().toString()}/remove">
                        @template.admin.components.csrfField(page.chrome().logoutForm())
                    </form>
                @endfor
            @else
                <table>
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
                </table>
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
                    <select id="contentItemId" name="contentItemId">
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
