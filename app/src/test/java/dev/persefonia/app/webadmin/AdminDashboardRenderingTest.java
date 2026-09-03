package dev.persefonia.app.webadmin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.persefonia.webadmin.AdminDashboardViewModel;
import dev.persefonia.webadmin.AuthenticatedAdminView;
import dev.persefonia.webadmin.LogoutFormViewModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;

class AdminDashboardRenderingTest {
    @Test
    void dashboardShellRendersNavigationPlaceholders() {
        assertThat(render()).contains(
                "Dashboard",
                "Content",
                "Profile",
                "Projects",
                "Media",
                "Contact",
                "Analytics",
                "Audit",
                "Settings");
    }

    @Test
    void dashboardLinksToAuditAdmin() {
        assertThat(render()).contains("href=\"/admin/audit\"");
    }

    @Test
    void dashboardLinksToContentAdmin() {
        assertThat(render()).contains("href=\"/admin/content\"");
    }

    @Test
    void dashboardLinksToProjectAdmin() {
        assertThat(render()).contains("href=\"/admin/projects\"");
    }

    @Test
    void dashboardLinksToMediaAdmin() {
        assertThat(render()).contains("href=\"/admin/media\"");
    }

    @Test
    void dashboardLinksToContactAdmin() {
        assertThat(render()).contains("href=\"/admin/contact\"");
    }

    @Test
    void dashboardLinksToAnalyticsAdmin() {
        assertThat(render()).contains("href=\"/admin/analytics\"");
    }

    @Test
    void dashboardShellContainsNoindexRobotsMeta() {
        assertThat(render()).contains("<meta name=\"robots\" content=\"noindex,nofollow,noarchive\">");
    }

    @Test
    void dashboardShellDoesNotExposePublicFeedDiscoveryLink() {
        assertThat(render())
                .doesNotContain("application/atom+xml")
                .doesNotContain("/feed.xml");
    }

    @Test
    void dashboardShellContainsCsrfLogoutForm() {
        assertThat(render())
                .contains("<form method=\"post\" action=\"/logout\">")
                .contains("name=\"_csrf\"")
                .contains("value=\"csrf-token\"");
    }

    @Test
    void dashboardShellDoesNotRenderEmailSubjectOrTokens() {
        assertThat(render()).doesNotContain(
                "admin@example.com",
                "opaque-subject",
                "fake-id-token-value",
                "access_token",
                "refresh_token",
                "JSESSIONID");
    }

    private static String render() {
        TemplateEngine templateEngine = TemplateEngine.createPrecompiled(ContentType.Html);
        StringOutput output = new StringOutput();
        var page = AdminDashboardViewModel.shell(
                new AuthenticatedAdminView("Ada Admin", List.of("Owner"), true, false),
                new LogoutFormViewModel("/logout", "_csrf", "csrf-token"));

        templateEngine.render("admin/shell.jte", Map.of("page", page), output);
        return output.toString();
    }
}
