@import dev.persefonia.webadmin.discovery.AdminRedirectPage
@param AdminRedirectPage page
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex,nofollow,noarchive">
    <title>Redirects | Persefonia Admin</title>
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
            <input type="hidden" name="${page.chrome().logoutForm().csrfParameterName()}" value="${page.chrome().logoutForm().csrfToken()}">
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
                                    <input type="hidden" name="${page.chrome().logoutForm().csrfParameterName()}" value="${page.chrome().logoutForm().csrfToken()}">
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
</body>
</html>
