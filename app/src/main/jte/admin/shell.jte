@import dev.persefonia.webadmin.AdminDashboardViewModel
@param AdminDashboardViewModel page
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex,nofollow,noarchive">
    <title>${page.title()}</title>
</head>
<body>
    <header>
        <p>Signed in as <strong>${page.admin().displayName()}</strong></p>
        <ul aria-label="Local admin roles">
            @for(String roleLabel : page.admin().roleLabels())
                <li>${roleLabel}</li>
            @endfor
        </ul>
        <form method="post" action="${page.logoutForm().action()}">
            <input type="hidden"
                   name="${page.logoutForm().csrfParameterName()}"
                   value="${page.logoutForm().csrfToken()}">
            <button type="submit">Logout</button>
        </form>
    </header>
    <nav aria-label="Admin navigation">
        <ul>
            @for(var item : page.navigation())
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
    </main>
</body>
</html>
