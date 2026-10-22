@import dev.persefonia.webadmin.FieldErrors
@import dev.persefonia.webadmin.projects.AdminProjectFormPage
@param AdminProjectFormPage page
@param java.util.List<String> adminStylesheetPaths = java.util.List.of()
@template.admin.components.pageShell(page.heading() + " | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, adminStylesheetPaths = adminStylesheetPaths, content = @`
    <main id="admin-content" tabindex="-1">
        <h1>${page.heading()}</h1>
        <p><a href="/admin/projects">Back to projects</a></p>
        <p>Default language: ${page.defaultLanguage()}</p>
        @if(page.successMessage() != null)
            <p role="status">${page.successMessage()}</p>
        @endif
        @if(page.hasErrors())
            @for(var error : page.globalErrors())
                <p role="alert">${error}</p>
            @endfor
            @template.admin.components.fieldErrors(page.fieldErrors())
        @endif
        <form method="post" action="${page.action()}">
            @template.admin.components.csrfField(page.chrome().logoutForm())

            <label for="status">Status</label>
            <select id="status" name="status" aria-invalid="${FieldErrors.has(page.fieldErrors(), "status") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "status") ? "error-status" : null}">
                @for(var value : java.util.List.of("ACTIVE", "COMPLETED", "ARCHIVED", "EXPERIMENT"))
                    <option value="${value}" selected="${value.equals(page.form().getStatus())}">${value}</option>
                @endfor
            </select>

            <label for="visibility">Visibility</label>
            <select id="visibility" name="visibility" aria-invalid="${FieldErrors.has(page.fieldErrors(), "visibility") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "visibility") ? "error-visibility" : null}">
                @for(var value : java.util.List.of("PUBLIC", "UNLISTED", "PRIVATE"))
                    <option value="${value}" selected="${value.equals(page.form().getVisibility())}">${value}</option>
                @endfor
            </select>

            <label><input type="checkbox" name="featured" value="true" checked="${page.form().isFeatured()}" id="featured" aria-invalid="${FieldErrors.has(page.fieldErrors(), "featured") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "featured") ? "error-featured" : null}"> Featured</label>

            <label for="coverAssetId">Gallery cover image asset ID</label>
            <input id="coverAssetId" name="coverAssetId" value="${page.form().getCoverAssetId()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "coverAssetId") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "coverAssetId") ? "error-coverAssetId" : null}">
            <p>Attach an image from the media library. Only processed public image variants appear in the homepage gallery. Leave blank to remove the cover.</p>

            <label for="sortOrder">Sort order</label>
            <input id="sortOrder" name="sortOrder" inputmode="numeric" value="${page.form().getSortOrder()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "sortOrder") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "sortOrder") ? "error-sortOrder" : null}">

            <div id="slug" tabindex="-1" aria-describedby="${FieldErrors.has(page.fieldErrors(), "slug") ? "error-slug" : null}">
            <fieldset>
                <legend>TR</legend>
                <label><input type="checkbox" name="trEnabled" value="true" checked="${page.form().isTrEnabled()}" id="trEnabled" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trEnabled") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trEnabled") ? "error-trEnabled" : null}"> Enabled</label>
                <label for="trSlug">Slug</label>
                <input id="trSlug" name="trSlug" maxlength="100" value="${page.form().getTrSlug()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trSlug") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trSlug") ? "error-trSlug" : null}">
                <label for="trTitle">Title</label>
                <input id="trTitle" name="trTitle" maxlength="180" value="${page.form().getTrTitle()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trTitle") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trTitle") ? "error-trTitle" : null}">
                <label for="trSummary">Summary</label>
                <textarea id="trSummary" name="trSummary" maxlength="1000" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trSummary") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trSummary") ? "error-trSummary" : null}">${page.form().getTrSummary()}</textarea>
                <label for="trProblem">Problem</label>
                <textarea id="trProblem" name="trProblem" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trProblem") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trProblem") ? "error-trProblem" : null}">${page.form().getTrProblem()}</textarea>
                <label for="trContext">Context</label>
                <textarea id="trContext" name="trContext" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trContext") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trContext") ? "error-trContext" : null}">${page.form().getTrContext()}</textarea>
                <label for="trRole">Role</label>
                <textarea id="trRole" name="trRole" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trRole") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trRole") ? "error-trRole" : null}">${page.form().getTrRole()}</textarea>
                <label for="trApproach">Approach</label>
                <textarea id="trApproach" name="trApproach" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trApproach") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trApproach") ? "error-trApproach" : null}">${page.form().getTrApproach()}</textarea>
                <label for="trArchitecture">Architecture</label>
                <textarea id="trArchitecture" name="trArchitecture" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trArchitecture") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trArchitecture") ? "error-trArchitecture" : null}">${page.form().getTrArchitecture()}</textarea>
                <label for="trDecisions">Decisions</label>
                <textarea id="trDecisions" name="trDecisions" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trDecisions") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trDecisions") ? "error-trDecisions" : null}">${page.form().getTrDecisions()}</textarea>
                <label for="trTradeoffs">Tradeoffs</label>
                <textarea id="trTradeoffs" name="trTradeoffs" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trTradeoffs") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trTradeoffs") ? "error-trTradeoffs" : null}">${page.form().getTrTradeoffs()}</textarea>
                <label for="trResult">Result</label>
                <textarea id="trResult" name="trResult" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trResult") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trResult") ? "error-trResult" : null}">${page.form().getTrResult()}</textarea>
                <label for="trLessons">Lessons</label>
                <textarea id="trLessons" name="trLessons" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trLessons") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trLessons") ? "error-trLessons" : null}">${page.form().getTrLessons()}</textarea>
                <label for="trFuture">Future</label>
                <textarea id="trFuture" name="trFuture" aria-invalid="${FieldErrors.has(page.fieldErrors(), "trFuture") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "trFuture") ? "error-trFuture" : null}">${page.form().getTrFuture()}</textarea>
            </fieldset>

            <fieldset>
                <legend>EN</legend>
                <label><input type="checkbox" name="enEnabled" value="true" checked="${page.form().isEnEnabled()}" id="enEnabled" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enEnabled") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enEnabled") ? "error-enEnabled" : null}"> Enabled</label>
                <label for="enSlug">Slug</label>
                <input id="enSlug" name="enSlug" maxlength="100" value="${page.form().getEnSlug()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enSlug") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enSlug") ? "error-enSlug" : null}">
                <label for="enTitle">Title</label>
                <input id="enTitle" name="enTitle" maxlength="180" value="${page.form().getEnTitle()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enTitle") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enTitle") ? "error-enTitle" : null}">
                <label for="enSummary">Summary</label>
                <textarea id="enSummary" name="enSummary" maxlength="1000" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enSummary") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enSummary") ? "error-enSummary" : null}">${page.form().getEnSummary()}</textarea>
                <label for="enProblem">Problem</label>
                <textarea id="enProblem" name="enProblem" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enProblem") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enProblem") ? "error-enProblem" : null}">${page.form().getEnProblem()}</textarea>
                <label for="enContext">Context</label>
                <textarea id="enContext" name="enContext" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enContext") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enContext") ? "error-enContext" : null}">${page.form().getEnContext()}</textarea>
                <label for="enRole">Role</label>
                <textarea id="enRole" name="enRole" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enRole") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enRole") ? "error-enRole" : null}">${page.form().getEnRole()}</textarea>
                <label for="enApproach">Approach</label>
                <textarea id="enApproach" name="enApproach" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enApproach") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enApproach") ? "error-enApproach" : null}">${page.form().getEnApproach()}</textarea>
                <label for="enArchitecture">Architecture</label>
                <textarea id="enArchitecture" name="enArchitecture" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enArchitecture") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enArchitecture") ? "error-enArchitecture" : null}">${page.form().getEnArchitecture()}</textarea>
                <label for="enDecisions">Decisions</label>
                <textarea id="enDecisions" name="enDecisions" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enDecisions") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enDecisions") ? "error-enDecisions" : null}">${page.form().getEnDecisions()}</textarea>
                <label for="enTradeoffs">Tradeoffs</label>
                <textarea id="enTradeoffs" name="enTradeoffs" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enTradeoffs") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enTradeoffs") ? "error-enTradeoffs" : null}">${page.form().getEnTradeoffs()}</textarea>
                <label for="enResult">Result</label>
                <textarea id="enResult" name="enResult" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enResult") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enResult") ? "error-enResult" : null}">${page.form().getEnResult()}</textarea>
                <label for="enLessons">Lessons</label>
                <textarea id="enLessons" name="enLessons" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enLessons") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enLessons") ? "error-enLessons" : null}">${page.form().getEnLessons()}</textarea>
                <label for="enFuture">Future</label>
                <textarea id="enFuture" name="enFuture" aria-invalid="${FieldErrors.has(page.fieldErrors(), "enFuture") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "enFuture") ? "error-enFuture" : null}">${page.form().getEnFuture()}</textarea>
            </fieldset>

            </div>
            <label for="technologies">Technologies</label>
            <p>Name | CATEGORY</p>
            <textarea id="technologies" name="technologies" aria-invalid="${FieldErrors.has(page.fieldErrors(), "technologies") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "technologies") ? "error-technologies" : null}">${page.form().getTechnologies()}</textarea>

            <label for="links">Links</label>
            <p>Label | https://example.com | TYPE</p>
            <textarea id="links" name="links" aria-invalid="${FieldErrors.has(page.fieldErrors(), "links") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "links") ? "error-links" : null}">${page.form().getLinks()}</textarea>

            <fieldset id="tagIds" tabindex="-1" aria-describedby="${FieldErrors.has(page.fieldErrors(), "tagIds") ? "error-tagIds" : null}">
                <legend>Tags</legend>
                @if(page.assignableTags().isEmpty() && page.assignedTags().stream().noneMatch(tag -> tag.archived()))
                    <p>No assignable tags are available.</p>
                @else
                    @for(var tag : page.assignableTags())
                        <label>
                            <input type="checkbox"
                                   name="tagIds"
                                   value="${tag.id().toString()}"
                                   checked="${page.form().getTagIds().contains(tag.id().toString())}" id="tag-${tag.id().toString()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "tagIds") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "tagIds") ? "error-tagIds" : null}">
                            ${tag.name()}
                        </label>
                    @endfor
                    @for(var tag : page.assignedTags())
                        @if(tag.archived())
                            <label>
                                <input type="checkbox"
                                       name="tagIds"
                                       value="${tag.id().toString()}"
                                       checked="${page.form().getTagIds().contains(tag.id().toString())}" id="tag-${tag.id().toString()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "tagIds") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "tagIds") ? "error-tagIds" : null}">
                                ${tag.name()} <strong>ARCHIVED</strong>
                            </label>
                        @endif
                    @endfor
                @endif
            </fieldset>

            <button type="submit">Save project</button>
        </form>
    </main>
`)
