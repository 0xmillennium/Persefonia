@import dev.persefonia.webadmin.AdminDashboardViewModel
@param AdminDashboardViewModel page
@template.admin.components.pageShell(page.title(), page.admin(), page.navigation(), page.logoutForm(), true, content = @`
    <main>
        <h1>${page.heading()}</h1>
    </main>
`)
