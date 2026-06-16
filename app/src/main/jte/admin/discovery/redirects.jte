@import dev.persefonia.webadmin.discovery.AdminRedirectPage
@param AdminRedirectPage page
@template.admin.components.pageShell("Redirects | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
<main>
    <h1>Redirects</h1>
    @if(page.successMessage() != null)
        <p role="status">${page.successMessage()}</p>
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
    <section aria-label="Create redirect">
        <h2>Create manual redirect</h2>
        <form method="post" action="/admin/discovery/redirects">
            @template.admin.components.csrfField(page.chrome().logoutForm())
            <label for="sourceUrl">Source path</label>
            <input id="sourceUrl" name="sourceUrl" value="${page.form().getSourceUrl()}">
            <label for="targetUrl">Target path</label>
            <input id="targetUrl" name="targetUrl" value="${page.form().getTargetUrl()}">
            <label for="statusCode">Status code</label>
            <select id="statusCode" name="statusCode">
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
            <table>
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
            </table>
        @endif
    </section>
</main>
`)
