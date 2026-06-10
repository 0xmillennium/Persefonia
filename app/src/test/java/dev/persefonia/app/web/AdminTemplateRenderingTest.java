package dev.persefonia.app.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import java.util.List;

import dev.persefonia.webadmin.AdminDashboardViewModel;
import dev.persefonia.webadmin.AuthenticatedAdminView;
import dev.persefonia.webadmin.LogoutFormViewModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;

class AdminTemplateRenderingTest {
    @Test
    void rendersPrecompiledAdminTemplate() {
        TemplateEngine templateEngine = TemplateEngine.createPrecompiled(ContentType.Html);
        StringOutput output = new StringOutput();

        templateEngine.render(
                "admin/shell.jte",
                Map.of("page", AdminDashboardViewModel.shell(
                        new AuthenticatedAdminView("Ada Admin", List.of("Owner"), true, false),
                        new LogoutFormViewModel("/logout", "_csrf", "csrf-token"))),
                output);

        assertTrue(output.toString().contains("Persefonia Admin"));
    }
}
