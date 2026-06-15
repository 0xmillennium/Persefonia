@import dev.persefonia.webadmin.AdminNavigationItem
@import dev.persefonia.webadmin.AuthenticatedAdminView
@import dev.persefonia.webadmin.LogoutFormViewModel
@param String title
@param AuthenticatedAdminView admin
@param java.util.List<AdminNavigationItem> navigation
@param LogoutFormViewModel logoutForm
@param boolean showRoles
@param gg.jte.html.HtmlContent content
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex,nofollow,noarchive">
    <title>${title}</title>
</head>
<body>
    @template.admin.components.adminHeader(admin, logoutForm, showRoles)
    @template.admin.components.adminNavigation(navigation)
    ${content}
</body>
</html>
