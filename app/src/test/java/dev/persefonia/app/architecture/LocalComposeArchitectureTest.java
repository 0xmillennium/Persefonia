package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

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
                        "PERSEFONIA_CLOUDFLARE_ZONE_ID", "PERSEFONIA_ADMIN_REQUIRED_OIDC_GROUP", "docker,prod");
    }

    @Test
    void healthchecksUseNativeCommandsAndOnlyApplicationReadiness() throws Exception {
        Map<?, ?> services = services();
        Map<?, ?> app = mapping(services.get("app"));
        Map<?, ?> postgres = mapping(services.get("postgres"));
        Map<?, ?> redis = mapping(services.get("redis"));

        assertThat(mapping(app.get("healthcheck")).get("test")).isEqualTo(List.of(
                "CMD", "curl", "--fail", "--silent", "--show-error", "--output", "/dev/null",
                "http://127.0.0.1:${PERSEFONIA_MANAGEMENT_PORT:?}/actuator/health/readiness"));
        Map<?, ?> databaseEnvironment = mapping(postgres.get("environment"));
        assertThat(mapping(postgres.get("healthcheck")).get("test")).isEqualTo(List.of(
                "CMD", "pg_isready", "-U", databaseEnvironment.get("POSTGRES_USER"),
                "-d", databaseEnvironment.get("POSTGRES_DB")));
        assertThat(mapping(redis.get("healthcheck")).get("test"))
                .isEqualTo(List.of("CMD", "redis-cli", "ping"));
        assertThat(mapping(app.get("environment")).get("PERSEFONIA_MANAGEMENT_PORT"))
                .isEqualTo("${PERSEFONIA_MANAGEMENT_PORT:?}");
    }

    @Test
    void redisClientAndAclStartupShareEnvironmentOwnedUsernameAndKeyPrefix() throws Exception {
        Map<?, ?> services = services();
        Map<?, ?> appEnvironment = mapping(mapping(services.get("app")).get("environment"));
        Map<?, ?> redis = mapping(services.get("redis"));
        Map<?, ?> redisEnvironment = mapping(redis.get("environment"));

        assertThat(redis.get("command")).isEqualTo(List.of("/usr/local/bin/redis-start.sh"));
        assertThat(redisEnvironment.get("PERSEFONIA_REDIS_USERNAME"))
                .isEqualTo("${PERSEFONIA_REDIS_USERNAME:?}")
                .isEqualTo(appEnvironment.get("SPRING_DATA_REDIS_USERNAME"));
        assertThat(redisEnvironment.get("PERSEFONIA_REDIS_KEY_PREFIX"))
                .isEqualTo("${PERSEFONIA_REDIS_KEY_PREFIX:?}")
                .isEqualTo(appEnvironment.get("PERSEFONIA_CONTACT_RATE_LIMIT_REDIS_KEY_PREFIX"));
        assertReadOnlyBind(redis, "./docker/redis-start.sh", "/usr/local/bin/redis-start.sh");
        assertReadOnlyBind(redis, "./docker/redis/redis.conf", "/usr/local/etc/redis/redis.conf");
        assertThat(Files.isExecutable(Path.of("../docker/redis-start.sh"))).isTrue();

        Properties environmentExample = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("../.env.example"))) {
            environmentExample.load(reader);
        }
        assertThat(environmentExample.getProperty("PERSEFONIA_REDIS_USERNAME")).isEqualTo("persefonia");
        assertThat(environmentExample.getProperty("PERSEFONIA_REDIS_KEY_PREFIX")).isEqualTo("persefonia:rate-limit");
        assertThat(environmentExample.containsKey("PERSEFONIA_ADMIN_REQUIRED_OIDC_GROUP")).isFalse();
    }

    @Test
    void postgresConsumesSharedConfigurationAndRetainsDataVolumeAndTmpfs() throws Exception {
        Map<?, ?> postgres = mapping(services().get("postgres"));

        assertThat(postgres.get("command"))
                .isEqualTo(List.of("postgres", "-c", "config_file=/etc/postgresql/postgresql.conf"));
        assertReadOnlyBind(postgres, "./docker/postgresql/postgresql.conf", "/etc/postgresql/postgresql.conf");
        assertReadOnlyBind(postgres, "./docker/postgresql/pg_hba.conf", "/etc/postgresql/pg_hba.conf");
        assertThat((List<?>) postgres.get("volumes"))
                .anyMatch(volume -> "postgres-data:/var/lib/postgresql/data".equals(volume));
        assertThat(postgres.get("tmpfs")).isEqualTo(List.of("/tmp", "/var/run/postgresql"));
        assertThat(mapping(postgres.get("environment")).get("POSTGRES_PASSWORD_FILE"))
                .isEqualTo("/run/secrets/postgres_password");
    }

    private static void assertReadOnlyBind(Map<?, ?> service, String source, String target) {
        List<?> volumes = (List<?>) service.get("volumes");
        Map<?, ?> mount = volumes.stream().filter(Map.class::isInstance).map(LocalComposeArchitectureTest::mapping)
                .filter(volume -> source.equals(volume.get("source"))).findFirst().orElseThrow();
        assertThat(mount.get("type")).isEqualTo("bind");
        assertThat(mount.get("target")).isEqualTo(target);
        assertThat(mount.get("read_only")).isEqualTo(true);
        assertThat(mapping(mount.get("bind")).get("create_host_path")).isEqualTo(false);
    }

    private static Map<?, ?> services() throws Exception {
        return mapping(mapping(new Yaml().load(Files.readString(LOCAL_COMPOSE))).get("services"));
    }

    private static Map<?, ?> mapping(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<?, ?>) value;
    }
}
