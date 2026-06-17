@import dev.persefonia.webadmin.projects.AdminProjectFormPage
@param AdminProjectFormPage page
@template.admin.components.pageShell(page.heading() + " | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
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
            @for(var error : page.fieldErrors())
                <p role="alert">${error.field()}: ${error.message()}</p>
            @endfor
        @endif
        <form method="post" action="${page.action()}">
            @template.admin.components.csrfField(page.chrome().logoutForm())

            <label for="status">Status</label>
            <select id="status" name="status">
                @for(var value : java.util.List.of("ACTIVE", "COMPLETED", "ARCHIVED", "EXPERIMENT"))
                    <option value="${value}" selected="${value.equals(page.form().getStatus())}">${value}</option>
                @endfor
            </select>

            <label for="visibility">Visibility</label>
            <select id="visibility" name="visibility">
                @for(var value : java.util.List.of("PUBLIC", "UNLISTED", "PRIVATE"))
                    <option value="${value}" selected="${value.equals(page.form().getVisibility())}">${value}</option>
                @endfor
            </select>

            <label><input type="checkbox" name="featured" value="true" checked="${page.form().isFeatured()}"> Featured</label>

            <label for="sortOrder">Sort order</label>
            <input id="sortOrder" name="sortOrder" inputmode="numeric" value="${page.form().getSortOrder()}">

            <fieldset>
                <legend>TR</legend>
                <label><input type="checkbox" name="trEnabled" value="true" checked="${page.form().isTrEnabled()}"> Enabled</label>
                <label for="trSlug">Slug</label>
                <input id="trSlug" name="trSlug" maxlength="100" value="${page.form().getTrSlug()}">
                <label for="trTitle">Title</label>
                <input id="trTitle" name="trTitle" maxlength="180" value="${page.form().getTrTitle()}">
                <label for="trSummary">Summary</label>
                <textarea id="trSummary" name="trSummary" maxlength="1000">${page.form().getTrSummary()}</textarea>
                <label for="trProblem">Problem</label>
                <textarea id="trProblem" name="trProblem">${page.form().getTrProblem()}</textarea>
                <label for="trContext">Context</label>
                <textarea id="trContext" name="trContext">${page.form().getTrContext()}</textarea>
                <label for="trRole">Role</label>
                <textarea id="trRole" name="trRole">${page.form().getTrRole()}</textarea>
                <label for="trApproach">Approach</label>
                <textarea id="trApproach" name="trApproach">${page.form().getTrApproach()}</textarea>
                <label for="trArchitecture">Architecture</label>
                <textarea id="trArchitecture" name="trArchitecture">${page.form().getTrArchitecture()}</textarea>
                <label for="trDecisions">Decisions</label>
                <textarea id="trDecisions" name="trDecisions">${page.form().getTrDecisions()}</textarea>
                <label for="trTradeoffs">Tradeoffs</label>
                <textarea id="trTradeoffs" name="trTradeoffs">${page.form().getTrTradeoffs()}</textarea>
                <label for="trResult">Result</label>
                <textarea id="trResult" name="trResult">${page.form().getTrResult()}</textarea>
                <label for="trLessons">Lessons</label>
                <textarea id="trLessons" name="trLessons">${page.form().getTrLessons()}</textarea>
                <label for="trFuture">Future</label>
                <textarea id="trFuture" name="trFuture">${page.form().getTrFuture()}</textarea>
            </fieldset>

            <fieldset>
                <legend>EN</legend>
                <label><input type="checkbox" name="enEnabled" value="true" checked="${page.form().isEnEnabled()}"> Enabled</label>
                <label for="enSlug">Slug</label>
                <input id="enSlug" name="enSlug" maxlength="100" value="${page.form().getEnSlug()}">
                <label for="enTitle">Title</label>
                <input id="enTitle" name="enTitle" maxlength="180" value="${page.form().getEnTitle()}">
                <label for="enSummary">Summary</label>
                <textarea id="enSummary" name="enSummary" maxlength="1000">${page.form().getEnSummary()}</textarea>
                <label for="enProblem">Problem</label>
                <textarea id="enProblem" name="enProblem">${page.form().getEnProblem()}</textarea>
                <label for="enContext">Context</label>
                <textarea id="enContext" name="enContext">${page.form().getEnContext()}</textarea>
                <label for="enRole">Role</label>
                <textarea id="enRole" name="enRole">${page.form().getEnRole()}</textarea>
                <label for="enApproach">Approach</label>
                <textarea id="enApproach" name="enApproach">${page.form().getEnApproach()}</textarea>
                <label for="enArchitecture">Architecture</label>
                <textarea id="enArchitecture" name="enArchitecture">${page.form().getEnArchitecture()}</textarea>
                <label for="enDecisions">Decisions</label>
                <textarea id="enDecisions" name="enDecisions">${page.form().getEnDecisions()}</textarea>
                <label for="enTradeoffs">Tradeoffs</label>
                <textarea id="enTradeoffs" name="enTradeoffs">${page.form().getEnTradeoffs()}</textarea>
                <label for="enResult">Result</label>
                <textarea id="enResult" name="enResult">${page.form().getEnResult()}</textarea>
                <label for="enLessons">Lessons</label>
                <textarea id="enLessons" name="enLessons">${page.form().getEnLessons()}</textarea>
                <label for="enFuture">Future</label>
                <textarea id="enFuture" name="enFuture">${page.form().getEnFuture()}</textarea>
            </fieldset>

            <label for="technologies">Technologies</label>
            <p>Name | CATEGORY</p>
            <textarea id="technologies" name="technologies">${page.form().getTechnologies()}</textarea>

            <label for="links">Links</label>
            <p>Label | https://example.com | TYPE</p>
            <textarea id="links" name="links">${page.form().getLinks()}</textarea>

            <fieldset>
                <legend>Tags</legend>
                @if(page.assignableTags().isEmpty() && page.assignedTags().stream().noneMatch(tag -> tag.archived()))
                    <p>No assignable tags are available.</p>
                @else
                    @for(var tag : page.assignableTags())
                        <label>
                            <input type="checkbox"
                                   name="tagIds"
                                   value="${tag.id().toString()}"
                                   checked="${page.form().getTagIds().contains(tag.id().toString())}">
                            ${tag.name()}
                        </label>
                    @endfor
                    @for(var tag : page.assignedTags())
                        @if(tag.archived())
                            <label>
                                <input type="checkbox"
                                       name="tagIds"
                                       value="${tag.id().toString()}"
                                       checked="${page.form().getTagIds().contains(tag.id().toString())}">
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
