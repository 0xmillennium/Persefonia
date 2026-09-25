package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LocalComposeArchitectureTest {
    private static final Path LOCAL_COMPOSE = Path.of("../compose.yaml");

    @Test
    void localDescriptorIsStandaloneAndPublishesOnlyLoopbackPorts() throws Exception {
        String descriptor = Files.readString(LOCAL_COMPOSE);

        try (Stream<Path> paths = Files.list(Path.of(".."))) {
            assertThat(paths.filter(path -> path.getFileName().toString().startsWith("compose")
                    && path.getFileName().toString().endsWith(".yaml")).toList())
                    .containsExactlyInAnyOrder(Path.of("../compose.yaml"), Path.of("../compose.production.yaml"));
        }
        assertThat(descriptor).contains("  app:", "  postgres:", "  redis:",
                "image: ${PERSEFONIA_IMAGE_REF:", "SPRING_PROFILES_ACTIVE: docker",
                "127.0.0.1:${PERSEFONIA_APP_PORT:",
                "127.0.0.1:${POSTGRES_PORT:", "127.0.0.1:${REDIS_PORT:",
                "  datanet:\n    internal: true", "      default: {}",
                "source: ${PERSEFONIA_MEDIA_HOST_PATH:",
                "target: /var/lib/persefonia/media", "create_host_path: false")
                .doesNotContain("build:", "backnet", "frontnet", "traefik.", "Authelia",
                        "PERSEFONIA_OIDC_ISSUER_URI", "PERSEFONIA_SMTP_HOST",
                        "PERSEFONIA_CLOUDFLARE_ZONE_ID", "docker,prod");
    }
}
