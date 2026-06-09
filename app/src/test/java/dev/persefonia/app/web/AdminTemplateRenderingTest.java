package dev.persefonia.app.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.persefonia.webadmin.AdminShellViewModel;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;

class AdminTemplateRenderingTest {
    @Test
    void rendersPrecompiledAdminTemplateWithoutExposingARoute() {
        TemplateEngine templateEngine = TemplateEngine.createPrecompiled(ContentType.Html);
        StringOutput output = new StringOutput();

        templateEngine.render(
                "admin/shell.jte",
                Map.of("page", new AdminShellViewModel(
                        "Persefonia Admin Shell",
                        "Authentication will be added in a later release.")),
                output);

        assertTrue(output.toString().contains("Persefonia Admin Shell"));
    }
}
