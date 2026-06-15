package dev.persefonia.app.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminSharedTemplateComponentsTest {
    private static final Path ADMIN_TEMPLATES = Path.of("src/main/jte/admin");

    @Test
    void adminFormsRenderSharedCsrfField() throws IOException {
        List<Path> directCsrfTemplates = templatesContaining("csrfParameterName()");

        assertThat(directCsrfTemplates)
                .extracting(path -> path.normalize().toString())
                .containsExactly("src/main/jte/admin/components/csrfField.jte");
    }

    @Test
    void adminPagesRenderSharedChrome() throws IOException {
        assertThat(templatesContaining("<header>"))
                .extracting(path -> path.normalize().toString())
                .containsExactly("src/main/jte/admin/components/adminHeader.jte");
        assertThat(templatesContaining("<nav aria-label=\"Admin navigation\">"))
                .extracting(path -> path.normalize().toString())
                .containsExactly("src/main/jte/admin/components/adminNavigation.jte");
    }

    private static List<Path> templatesContaining(String value) throws IOException {
        try (var paths = Files.walk(ADMIN_TEMPLATES)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> read(path).contains(value))
                    .sorted()
                    .toList();
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}
