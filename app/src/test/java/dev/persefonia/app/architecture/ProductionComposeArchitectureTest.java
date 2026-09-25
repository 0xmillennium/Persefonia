package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductionComposeArchitectureTest {
    private static final Path BASE_COMPOSE = Path.of("../compose.yaml");
    private static final Path PRODUCTION_COMPOSE = Path.of("../compose.production.yaml");

    @Test
    void productionApplicationReceivesBothAdminAllowlists() throws Exception {
        String application = block(Files.readString(PRODUCTION_COMPOSE), "  app:", 2);
        String environment = block(application, "    environment:", 4);

        assertThat(environment)
                .containsPattern("(?m)^      PERSEFONIA_ADMIN_ALLOWLISTED_SUBJECTS:")
                .containsPattern("(?m)^      PERSEFONIA_ADMIN_ALLOWLISTED_EMAILS:");
    }

    @Test
    void productionApplicationUsesAnExternalImageWithoutABuildDefinition() throws Exception {
        String baseApplication = block(Files.readString(BASE_COMPOSE), "  app:", 2);
        String productionApplication = block(Files.readString(PRODUCTION_COMPOSE), "  app:", 2);

        assertThat(baseApplication)
                .containsPattern("(?m)^    image: \\$\\{PERSEFONIA_IMAGE_REF:")
                .doesNotContainPattern("(?m)^    build:");
        assertThat(productionApplication).doesNotContainPattern("(?m)^    build:");
    }

    private static String block(String content, String header, int indentation) {
        String[] lines = content.split("\\R");
        StringBuilder result = new StringBuilder();
        boolean inside = false;
        for (String line : lines) {
            if (!inside) {
                inside = line.equals(header);
                continue;
            }
            if (!line.isBlank() && line.length() - line.stripLeading().length() <= indentation) {
                break;
            }
            result.append(line).append('\n');
        }
        assertThat(inside).as("Compose section %s", header).isTrue();
        return result.toString();
    }
}
