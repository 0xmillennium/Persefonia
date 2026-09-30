package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class LocalComposeArchitectureTest {
    private static final Path LOCAL_COMPOSE = Path.of("../compose.yaml");

    @Test
    void oneRootDescriptorOwnsOnlyLocalSourceServices() throws Exception {
        try (Stream<Path> paths = Files.list(Path.of(".."))) {
            assertThat(paths.filter(path -> path.getFileName().toString().startsWith("compose")
                    && path.getFileName().toString().endsWith(".yaml")).toList())
                    .containsExactlyInAnyOrder(LOCAL_COMPOSE, Path.of("../compose.override.example.yaml"));
        }
        Map<String, Object> descriptor = descriptor();
        assertThat(descriptor.get("name")).isEqualTo("persefonia-local");
        assertThat(mapping(descriptor.get("services")).keySet()).containsExactlyInAnyOrder("app", "postgres", "redis");
        assertThat(descriptor.containsKey("networks")).isFalse();
        assertThat(mapping(descriptor.get("secrets")).keySet())
                .containsExactlyInAnyOrder("postgres_password", "redis_password", "contact_rate_limit_secret");
        assertThat(Files.readString(LOCAL_COMPOSE)).doesNotContain(
                "PERSEFONIA_IMAGE_REF", "ghcr.io/0xmillennium/persefonia", "traefik.", "Authelia", "SMTP");
    }

    @Test
    void appBuildsCurrentSourceWithNonRootReadOnlyRuntimeAndReadiness() throws Exception {
        Map<String, Object> app = service("app");
        assertThat(app).doesNotContainKeys("build", "env_file", "labels", "privileged", "networks");
        assertThat(app.get("image").toString()).startsWith("ghcr.io/ringcentral-docker/node:22.22.2-jdk25@sha256:");
        assertThat(app.get("user")).isEqualTo("${PERSEFONIA_LOCAL_UID:-1000}:${PERSEFONIA_LOCAL_GID:-1000}");
        assertThat(app.get("working_dir")).isEqualTo("/workspace");
        assertThat(app.get("command")).isEqualTo(List.of("./gradlew", "--no-daemon", "--console=plain", ":app:bootRun"));
        assertThat(app.get("init")).isEqualTo(true);
        assertThat(app.get("read_only")).isEqualTo(true);
        assertThat(app.get("cap_drop")).isEqualTo(List.of("ALL"));
        assertThat(app.get("security_opt")).isEqualTo(List.of("no-new-privileges:true"));
        assertThat(app.get("tmpfs")).isEqualTo(List.of("/tmp"));
        assertThat(app.get("ports")).isEqualTo(List.of(
                "127.0.0.1:${PERSEFONIA_APP_PORT:-8080}:8080",
                "127.0.0.1:${PERSEFONIA_MANAGEMENT_PORT:-9001}:9001"));
        assertThat(mapping(app.get("healthcheck")).get("test")).isEqualTo(List.of(
                "CMD", "curl", "--fail", "--silent", "--show-error", "--output", "/dev/null",
                "http://127.0.0.1:9001/actuator/health/readiness"));
        Map<String, Object> dependency = mapping(app.get("depends_on"));
        for (String service : List.of("postgres", "redis")) {
            assertThat(mapping(dependency.get(service)).get("condition")).isEqualTo("service_healthy");
        }
        List<?> mounts = (List<?>) app.get("volumes");
        assertThat(mounts).hasSize(1);
        assertThat(mapping(mounts.getFirst())).containsEntry("type", "bind").containsEntry("source", ".")
                .containsEntry("target", "/workspace");
    }

    @Test
    void localApplicationInputsAreExplicitAndCredentialsUseFileSecrets() throws Exception {
        Map<String, Object> environment = mapping(service("app").get("environment"));
        assertThat(environment.get("SPRING_PROFILES_ACTIVE")).isEqualTo("local");
        assertThat(environment.get("PERSEFONIA_DATASOURCE_URL"))
                .isEqualTo("jdbc:postgresql://postgres:5432/${POSTGRES_DB}");
        assertThat(environment.get("PERSEFONIA_DATASOURCE_USERNAME")).isEqualTo("${POSTGRES_USER}");
        assertThat(environment).doesNotContainKeys("PERSEFONIA_DATASOURCE_PASSWORD", "SPRING_DATA_REDIS_PASSWORD",
                "PERSEFONIA_CONTACT_RATE_LIMIT_SECRET");
        assertThat(environment.get("SPRING_DATA_REDIS_USERNAME")).isEqualTo("${PERSEFONIA_REDIS_USERNAME}");
        assertThat(environment.get("PERSEFONIA_PUBLIC_BASE_URL")).isEqualTo("${PERSEFONIA_PUBLIC_BASE_URL}");
        assertThat(environment.get("PERSEFONIA_MEDIA_STORAGE_ROOT")).isEqualTo("/workspace/.runtime/media");
        assertThat(environment.get("PERSEFONIA_MEDIA_STORAGE_REQUIRED")).isEqualTo("true");
        assertThat(environment.get("PERSEFONIA_MANAGEMENT_ADDRESS")).isEqualTo("0.0.0.0");
        assertThat(environment.get("MANAGEMENT_ENDPOINT_HEALTH_GROUP_READINESS_INCLUDE"))
                .isEqualTo("readinessState,db,mediaStorage,databaseMigrations");
        assertThat(environment.get("HOME")).isEqualTo("/workspace/.runtime/home");
        assertThat(environment.get("GRADLE_USER_HOME")).isEqualTo("/workspace/.runtime/gradle");
        assertThat(environment.get("NPM_CONFIG_CACHE")).isEqualTo("/workspace/.runtime/npm");
    }

    @Test
    void postgresPersistsAndRedisRemainsDisposable() throws Exception {
        Map<String, Object> postgres = service("postgres");
        Map<String, Object> redis = service("redis");
        assertThat(postgres.get("volumes")).isEqualTo(List.of("postgres-data:/var/lib/postgresql/data"));
        assertThat(mapping(descriptor().get("volumes")).keySet()).containsExactly("postgres-data");
        assertThat(((List<?>) redis.get("volumes"))).hasSize(1);
        assertThat(mapping(((List<?>) redis.get("volumes")).getFirst()))
                .containsEntry("type", "bind").containsEntry("read_only", true);
        assertThat(redis.get("entrypoint")).isEqualTo(List.of("sh", "/usr/local/bin/persefonia-local-redis-entrypoint.sh"));
        assertThat(redis).doesNotContainKey("command");
        assertThat(postgres.get("ports")).isEqualTo(List.of("127.0.0.1:${PERSEFONIA_POSTGRES_PORT:-5432}:5432"));
        assertThat(redis.get("ports")).isEqualTo(List.of("127.0.0.1:${PERSEFONIA_REDIS_HOST_PORT:-6379}:6379"));
        assertThat(postgres).containsKey("healthcheck");
        assertThat(redis).containsKey("healthcheck");
    }

    private static Map<String, Object> descriptor() throws Exception {
        return mapping(new Yaml().load(Files.readString(LOCAL_COMPOSE)));
    }

    private static Map<String, Object> service(String name) throws Exception {
        return mapping(mapping(descriptor().get("services")).get(name));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapping(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
