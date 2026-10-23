@import dev.persefonia.webadmin.FieldErrors
@import dev.persefonia.webadmin.discovery.AdminRedirectPage
@param AdminRedirectPage page
@param java.util.List<String> adminStylesheetPaths = java.util.List.of()
@template.admin.components.pageShell("Redirects | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, adminStylesheetPaths = adminStylesheetPaths, content = @`
<main id="admin-content" tabindex="-1">
    <h1>Redirects</h1>
    @if(page.successMessage() != null)
        <p role="status">${page.successMessage()}</p>
    @endif
    @if(page.hasErrors())
        <section aria-label="Errors">
            @for(var error : page.globalErrors())
                <p role="alert">${error}</p>
            @endfor
            @template.admin.components.fieldErrors(page.fieldErrors())
        </section>
    @endif
    <section aria-label="Create redirect">
        <h2>Create manual redirect</h2>
        <form method="post" action="/admin/discovery/redirects">
            @template.admin.components.csrfField(page.chrome().logoutForm())
            <label for="sourceUrl">Source path</label>
            <input id="sourceUrl" name="sourceUrl" value="${page.form().getSourceUrl()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "sourceUrl") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "sourceUrl") ? "error-sourceUrl" : null}">
            <label for="targetUrl">Target path</label>
            <input id="targetUrl" name="targetUrl" value="${page.form().getTargetUrl()}" aria-invalid="${FieldErrors.has(page.fieldErrors(), "targetUrl") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "targetUrl") ? "error-targetUrl" : null}">
            <label for="statusCode">Status code</label>
            <select id="statusCode" name="statusCode" aria-invalid="${FieldErrors.has(page.fieldErrors(), "statusCode") ? "true" : null}" aria-describedby="${FieldErrors.has(page.fieldErrors(), "statusCode") ? "error-statusCode" : null}">
                @for(String value : java.util.List.of("301", "302", "307", "308"))
                    <option value="${value}" selected="${value.equals(page.form().getStatusCode())}">${value}</option>
                @endfor
            </select>
            <button type="submit">Create redirect</button>
        </form>
    </section>
    <section aria-label="Redirect rules">
        <h2>Redirect rules</h2>
        @if(page.rules().isEmpty())
            <p>No redirect rules yet.</p>
        @else
            <div class="admin-table-scroll" role="region" aria-label="Scrollable table" tabindex="0"><table>
                <thead>
                    <tr>
                        <th>Source</th>
                        <th>Target</th>
                        <th>Status</th>
                        <th>Reason</th>
                        <th>Active</th>
                        <th>Source ref</th>
                        <th>Created</th>
                        <th>Updated</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                @for(var rule : page.rules())
                    <tr>
                        <td>${rule.sourceUrl()}</td>
                        <td>${rule.targetUrl()}</td>
                        <td>${rule.statusCode()}</td>
                        <td>${rule.reason()}</td>
                        <td>${rule.activeLabel()}</td>
                        <td>${rule.sourceRef()}</td>
                        <td>${rule.createdAt()}</td>
                        <td>${rule.updatedAt()}</td>
                        <td>
                            @if(rule.active())
                                <form method="post" action="${rule.deactivateAction()}">
                                    @template.admin.components.csrfField(page.chrome().logoutForm())
                                    <button type="submit">Deactivate</button>
                                </form>
                            @else
                                <span aria-disabled="true">Inactive</span>
                            @endif
                        </td>
                    </tr>
                @endfor
                </tbody>
            </table></div>
        @endif
    </section>
</main>
`)
