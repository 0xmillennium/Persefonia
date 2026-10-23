@import dev.persefonia.webadmin.AdminDashboardViewModel
@param AdminDashboardViewModel page
@param java.util.List<String> adminStylesheetPaths = java.util.List.of()
@template.admin.components.pageShell(page.title(), page.admin(), page.navigation(), page.logoutForm(), true, adminStylesheetPaths = adminStylesheetPaths, content = @`
    <main id="admin-content" tabindex="-1">
        <h1>${page.heading()}</h1>
    </main>
`)
