package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class ApplicationComposeArchitectureTest {
    private static final Path APPLICATION_COMPOSE = Path.of("../compose.yaml");

    @Test
    void descriptorOwnsOnlyTheApplicationContainer() throws Exception {
        try (Stream<Path> paths = Files.list(Path.of(".."))) {
            assertThat(paths.filter(path -> path.getFileName().toString().startsWith("compose")
                    && path.getFileName().toString().endsWith(".yaml")).toList())
                    .containsExactly(APPLICATION_COMPOSE);
        }
        Map<?, ?> descriptor = descriptor();
        assertThat(mapping(descriptor.get("services")).keySet().stream().map(Object::toString).toList()).containsExactly("app");
        assertThat(descriptor.containsKey("volumes")).isFalse();
        assertThat(descriptor.containsKey("networks")).isFalse();
        Map<?, ?> app = app();
        for (String key : List.of("build", "depends_on", "networks", "labels")) {
            assertThat(app.containsKey(key)).as("app %s", key).isFalse();
        }
        assertThat(app.get("image"))
                .isEqualTo("${PERSEFONIA_IMAGE_REF:?PERSEFONIA_IMAGE_REF is required}");
        assertThat(Files.readString(APPLICATION_COMPOSE)).doesNotContain(
                "traefik.", "Authelia", "backnet", "frontnet", "docker,prod");
    }

    @Test
    void applicationRetainsHardeningReadinessAndLoopbackExposure() throws Exception {
        Map<?, ?> app = app();
        assertThat(app.get("user")).isEqualTo("10001:10001");
        assertThat(app.get("read_only")).isEqualTo(true);
        assertThat(app.get("cap_drop")).isEqualTo(List.of("ALL"));
        assertThat(app.get("security_opt")).isEqualTo(List.of("no-new-privileges:true"));
        assertThat(app.get("tmpfs")).isEqualTo(List.of("/tmp"));
        assertThat(app.get("ports")).isEqualTo(List.of(
                "127.0.0.1:${PERSEFONIA_APP_PORT:?PERSEFONIA_APP_PORT is required}:8080"));
        Map<?, ?> healthcheck = mapping(app.get("healthcheck"));
        assertThat(healthcheck.get("test")).isEqualTo(List.of(
                "CMD", "curl", "--fail", "--silent", "--show-error", "--output", "/dev/null",
                "http://127.0.0.1:${PERSEFONIA_MANAGEMENT_PORT:?}/actuator/health/readiness"));
        assertThat(healthcheck.get("interval")).isEqualTo("10s");
        assertThat(healthcheck.get("timeout")).isEqualTo("5s");
        assertThat(healthcheck.get("retries")).isEqualTo(12);
        assertThat(healthcheck.get("start_period")).isEqualTo("30s");
    }

    @Test
    void mediaIsDurableAndItsHostDirectoryMustAlreadyExist() throws Exception {
        List<?> volumes = (List<?>) app().get("volumes");
        assertThat(volumes).hasSize(1);
        Map<?, ?> mount = mapping(volumes.getFirst());
        assertThat(mount.get("type")).isEqualTo("bind");
        assertThat(mount.get("source"))
                .isEqualTo("${PERSEFONIA_MEDIA_HOST_PATH:?PERSEFONIA_MEDIA_HOST_PATH is required}");
        assertThat(mount.get("target")).isEqualTo("/var/lib/persefonia/media");
        assertThat(mapping(mount.get("bind")).get("create_host_path")).isEqualTo(false);
    }

    @Test
    void environmentContainsOnlyApplicationAndDependencyClientInputs() throws Exception {
        Map<?, ?> environment = mapping(app().get("environment"));
        assertThat(environment.keySet().stream().map(Object::toString).toList()).containsExactlyInAnyOrder(
                "SPRING_PROFILES_ACTIVE", "POSTGRES_DB", "POSTGRES_USER",
                "PERSEFONIA_POSTGRES_HOST", "PERSEFONIA_POSTGRES_PORT",
                "PERSEFONIA_REDIS_HOST", "PERSEFONIA_REDIS_PORT", "SPRING_DATA_REDIS_USERNAME",
                "PERSEFONIA_CONTACT_RATE_LIMIT_REDIS_KEY_PREFIX", "PERSEFONIA_MANAGEMENT_PORT");
        assertThat(environment.get("SPRING_PROFILES_ACTIVE")).isEqualTo("docker");
        for (String key : List.of("PERSEFONIA_POSTGRES_HOST", "PERSEFONIA_POSTGRES_PORT",
                "PERSEFONIA_REDIS_HOST", "PERSEFONIA_REDIS_PORT", "PERSEFONIA_MANAGEMENT_PORT")) {
            assertThat(environment.get(key)).isEqualTo("${" + key + ":?}");
        }
        assertThat(environment.get("POSTGRES_DB")).isEqualTo("${POSTGRES_DB:?POSTGRES_DB is required}");
        assertThat(environment.get("POSTGRES_USER")).isEqualTo("${POSTGRES_USER:?POSTGRES_USER is required}");
        assertThat(environment.get("SPRING_DATA_REDIS_USERNAME")).isEqualTo("${PERSEFONIA_REDIS_USERNAME:?}");
        assertThat(environment.get("PERSEFONIA_CONTACT_RATE_LIMIT_REDIS_KEY_PREFIX"))
                .isEqualTo("${PERSEFONIA_REDIS_KEY_PREFIX:?}");
    }

    @Test
    void appConsumesAllFiveApplicationSecrets() throws Exception {
        assertThat(app().get("secrets")).isEqualTo(List.of(
                Map.of("source", "postgres_password", "target", "spring.datasource.password"),
                Map.of("source", "redis_password", "target", "spring.data.redis.password"),
                Map.of("source", "contact_rate_limit_secret", "target", "persefonia.contact.rate-limit.secret"),
                Map.of("source", "oidc_client_secret", "target",
                        "spring.security.oauth2.client.registration.authelia.client-secret"),
                Map.of("source", "cloudflare_api_token", "target",
                        "persefonia.cache-purge.cloudflare.api-token")));
    }

    private static Map<?, ?> descriptor() throws Exception {
        return mapping(new Yaml().load(Files.readString(APPLICATION_COMPOSE)));
    }

    private static Map<?, ?> app() throws Exception {
        return mapping(mapping(descriptor().get("services")).get("app"));
    }

    private static Map<?, ?> mapping(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<?, ?>) value;
    }
}
