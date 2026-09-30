package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class ApplicationEnvironmentContractTest {
    @Test
    void templateContainsUsableFakeLocalInputsWithoutProductionArtifactOrSecretFiles() throws Exception {
        Properties inputs = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("../.env.example"))) {
            inputs.load(reader);
        }
        assertThat(inputs.stringPropertyNames()).containsExactlyInAnyOrder(
                "PERSEFONIA_LOCAL_UID", "PERSEFONIA_LOCAL_GID", "PERSEFONIA_APP_PORT",
                "PERSEFONIA_MANAGEMENT_PORT", "PERSEFONIA_POSTGRES_PORT", "PERSEFONIA_REDIS_HOST_PORT",
                "PERSEFONIA_PUBLIC_BASE_URL", "POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD",
                "REDIS_PASSWORD", "PERSEFONIA_REDIS_KEY_PREFIX", "PERSEFONIA_CONTACT_RATE_LIMIT_SECRET")
                .doesNotContain("PERSEFONIA_IMAGE_REF", "PERSEFONIA_MEDIA_HOST_PATH");
        assertThat(inputs.getProperty("PERSEFONIA_LOCAL_UID")).isEqualTo("1000");
        assertThat(inputs.getProperty("PERSEFONIA_LOCAL_GID")).isEqualTo("1000");
        assertThat(inputs.getProperty("PERSEFONIA_PUBLIC_BASE_URL")).isEqualTo("http://localhost:8080");
        assertThat(inputs.getProperty("POSTGRES_PASSWORD")).startsWith("local-only-");
        assertThat(inputs.getProperty("REDIS_PASSWORD")).startsWith("local-only-");
        assertThat(inputs.getProperty("PERSEFONIA_CONTACT_RATE_LIMIT_SECRET")).startsWith("local-only-");
        assertThat(Path.of("../.env.production.example")).doesNotExist();
    }

    @Test
    void localProfileRetainsOptionalFileSecrets() throws Exception {
        String profile = Files.readString(Path.of("src/main/resources/application-local.yml"));
        assertThat(profile).contains("optional:configtree:/run/secrets/", "${PERSEFONIA_DATASOURCE_URL}", "${PERSEFONIA_DATASOURCE_USERNAME}",
                "${PERSEFONIA_DATASOURCE_PASSWORD}");
    }

    @Test
    void dockerProfileDeclaresConfigurableDependencyEndpointsWithCompatibleDefaults() throws Exception {
        String profile = Files.readString(Path.of("src/main/resources/application-docker.yml"));
        assertThat(profile).contains(
                "url: jdbc:postgresql://${PERSEFONIA_POSTGRES_HOST:postgres}:${PERSEFONIA_POSTGRES_PORT:5432}/${POSTGRES_DB}",
                "username: ${POSTGRES_USER}", "host: ${PERSEFONIA_REDIS_HOST:redis}",
                "port: ${PERSEFONIA_REDIS_PORT:6379}");
        StandardEnvironment environment = dockerEnvironment(Map.of("POSTGRES_DB", "persefonia"));
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://postgres:5432/persefonia");
        assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("redis");
        assertThat(environment.getProperty("spring.data.redis.port")).isEqualTo("6379");
    }

    @Test
    void dockerProfileResolvesSuppliedDependencyEndpoints() throws Exception {
        StandardEnvironment environment = dockerEnvironment(Map.of(
                "POSTGRES_DB", "application", "POSTGRES_USER", "client",
                "PERSEFONIA_POSTGRES_HOST", "database.example.invalid", "PERSEFONIA_POSTGRES_PORT", "15432",
                "PERSEFONIA_REDIS_HOST", "cache.example.invalid", "PERSEFONIA_REDIS_PORT", "16379"));
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://database.example.invalid:15432/application");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("client");
        assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("cache.example.invalid");
        assertThat(environment.getProperty("spring.data.redis.port")).isEqualTo("16379");
    }

    private static StandardEnvironment dockerEnvironment(Map<String, Object> inputs) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        new YamlPropertySourceLoader()
                .load("application-docker.yml", new ClassPathResource("application-docker.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        environment.getPropertySources().addFirst(new MapPropertySource("synthetic-inputs", inputs));
        return environment;
    }
}
