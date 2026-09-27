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
    void templateContainsOnlyApplicationInputsAndCompatibleDependencyDefaults() throws Exception {
        Properties inputs = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("../.env.example"))) {
            inputs.load(reader);
        }
        assertThat(inputs.stringPropertyNames()).containsExactlyInAnyOrder(
                "PERSEFONIA_IMAGE_REF", "POSTGRES_DB", "POSTGRES_USER",
                "PERSEFONIA_POSTGRES_HOST", "PERSEFONIA_POSTGRES_PORT",
                "PERSEFONIA_REDIS_HOST", "PERSEFONIA_REDIS_PORT",
                "PERSEFONIA_POSTGRES_PASSWORD_FILE", "PERSEFONIA_REDIS_PASSWORD_FILE",
                "PERSEFONIA_CONTACT_RATE_LIMIT_SECRET_FILE", "PERSEFONIA_OIDC_CLIENT_SECRET_FILE",
                "PERSEFONIA_CLOUDFLARE_API_TOKEN_FILE", "PERSEFONIA_REDIS_USERNAME",
                "PERSEFONIA_REDIS_KEY_PREFIX", "PERSEFONIA_APP_PORT",
                "PERSEFONIA_MANAGEMENT_PORT", "PERSEFONIA_MEDIA_HOST_PATH")
                .doesNotContain("POSTGRES_PORT", "REDIS_PORT");
        assertThat(inputs.getProperty("PERSEFONIA_POSTGRES_HOST")).isEqualTo("postgres");
        assertThat(inputs.getProperty("PERSEFONIA_POSTGRES_PORT")).isEqualTo("5432");
        assertThat(inputs.getProperty("PERSEFONIA_REDIS_HOST")).isEqualTo("redis");
        assertThat(inputs.getProperty("PERSEFONIA_REDIS_PORT")).isEqualTo("6379");
        assertThat(inputs.getProperty("PERSEFONIA_REDIS_USERNAME")).isEqualTo("persefonia");
        assertThat(inputs.getProperty("PERSEFONIA_REDIS_KEY_PREFIX")).isEqualTo("persefonia:rate-limit");
        assertThat(inputs.getProperty("PERSEFONIA_OIDC_CLIENT_SECRET_FILE"))
                .isEqualTo("./secrets/oidc_client_secret");
        assertThat(inputs.getProperty("PERSEFONIA_CLOUDFLARE_API_TOKEN_FILE"))
                .isEqualTo("./secrets/cloudflare_api_token");
        assertThat(Path.of("../.env.production.example")).doesNotExist();
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
