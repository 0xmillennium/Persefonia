package dev.persefonia.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointProperties;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class DockerHealthGroupConfigurationTest {
    @Test
    void dockerProfileDefinesTheRequiredHealthGroupMembership() throws IOException {
        HealthEndpointProperties health = dockerHealthProperties();

        assertThat(health.getGroup().get("liveness").getInclude())
                .containsExactly("livenessState");
        assertThat(health.getGroup().get("readiness").getInclude())
                .containsExactlyInAnyOrder("readinessState", "db", "mediaStorage")
                .doesNotContain("redis");
    }

    private static HealthEndpointProperties dockerHealthProperties() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("docker");
        new YamlPropertySourceLoader()
                .load("application-docker.yml", new ClassPathResource("application-docker.yml"))
                .forEach(source -> environment.getPropertySources().addFirst(source));

        return Binder.get(environment)
                .bind("management.endpoint.health", Bindable.of(HealthEndpointProperties.class))
                .orElseThrow(() -> new IllegalStateException("Docker health groups were not bound"));
    }
}
