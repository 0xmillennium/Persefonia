package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ProductionComposeArchitectureTest {
    private static final Path PRODUCTION_COMPOSE = Path.of("../compose.production.yaml");

    @Test
    void productionDescriptorOwnsCompleteRuntimeAndFixedProjectIdentity() throws Exception {
        String descriptor = Files.readString(PRODUCTION_COMPOSE);
        String app = block(descriptor, "  app:", 2);
        String postgres = block(descriptor, "  postgres:", 2);
        String redis = block(descriptor, "  redis:", 2);

        assertThat(descriptor).startsWith("name: persefonia\n")
                .contains("volumes:\n  postgres-data:", "  datanet:\n    internal: true",
                        "  backnet:\n    external: true\n    name: backnet",
                        "  frontnet:\n    external: true\n    name: frontnet")
                .doesNotContain("ports:", "build:", "persefonia-internal", "persefonia-egress");
        assertThat(app).contains("image: ${PERSEFONIA_IMAGE_REF:", "SPRING_PROFILES_ACTIVE: docker,prod",
                "PERSEFONIA_ADMIN_ALLOWLISTED_SUBJECTS:", "PERSEFONIA_ADMIN_ALLOWLISTED_EMAILS:",
                "PERSEFONIA_MANAGEMENT_ADDRESS: 0.0.0.0", "PERSEFONIA_MANAGEMENT_PORT: \"9001\"",
                "PERSEFONIA_SMTP_HOST: postfix-internal", "PERSEFONIA_SMTP_PORT: \"25\"",
                "PERSEFONIA_OIDC_CLIENT_ID: persefonia", "source: /var/lib/persefonia/media",
                "target: /var/lib/persefonia/media", "create_host_path: false",
                "gw_priority: 1", "- persefonia-app");
        assertThat(postgres).contains("image: postgres:", "- postgres-data:/var/lib/postgresql/data",
                "networks:\n      - datanet").doesNotContain("backnet", "frontnet");
        assertThat(redis).contains("image: redis:", "source: ./docker/redis-start.sh",
                "networks:\n      - datanet").doesNotContain("backnet", "frontnet");
    }

    @Test
    void productionRoutesPublicAndAdminThroughExternalProfilesOnly() throws Exception {
        String descriptor = Files.readString(PRODUCTION_COMPOSE);
        String labels = block(block(descriptor, "  app:", 2), "    labels:", 4);

        assertThat(labels).contains("traefik.docker.network: backnet",
                "traefik.http.routers.persefonia.middlewares: profile-persefonia-public@file",
                "traefik.http.routers.persefonia-admin.middlewares: profile-persefonia-admin@file",
                "Path(`/admin`)", "PathPrefix(`/admin/`)",
                "traefik.http.services.persefonia.loadbalancer.server.port: \"8080\"")
                .doesNotContain("traefik.http.middlewares.", "forwardAuth", "ForwardAuth",
                        "mw-auth", "mw-headers", "persefonia-body-limit", "9001");
        assertThat(priority(labels, "persefonia-admin")).isGreaterThan(priority(labels, "persefonia"));
    }

    private static int priority(String labels, String router) {
        Matcher match = Pattern.compile("(?m)^      traefik\\.http\\.routers\\."
                + router + "\\.priority: \"([0-9]+)\"$").matcher(labels);
        assertThat(match.find()).as("router %s has explicit priority", router).isTrue();
        return Integer.parseInt(match.group(1));
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
