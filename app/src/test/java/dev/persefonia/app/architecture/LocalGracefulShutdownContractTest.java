package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;
import org.yaml.snakeyaml.Yaml;

class LocalGracefulShutdownContractTest {
    @Test
    void composeStopAllowanceExceedsSpringShutdownPhaseBudget() throws Exception {
        Map<?, ?> compose = mapping(new Yaml().load(Files.readString(Path.of("../compose.yaml"))));
        Map<?, ?> application = mapping(new Yaml().load(
                Files.readString(Path.of("src/main/resources/application.yml"))));
        Object stopGrace = mapping(mapping(compose.get("services")).get("app")).get("stop_grace_period");
        Object phaseBudget = mapping(mapping(application.get("spring")).get("lifecycle"))
                .get("timeout-per-shutdown-phase");

        Duration composeDuration = requiredDuration(stopGrace, "services.app.stop_grace_period");
        Duration springDuration = requiredDuration(phaseBudget, "spring.lifecycle.timeout-per-shutdown-phase");
        assertThat(composeDuration).isGreaterThan(springDuration);
    }

    @Test
    void rejectsAbsentMalformedEqualAndInsufficientBudgets() {
        assertThatThrownBy(() -> requireMargin(null, "20s")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requireMargin("30s", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requireMargin("not-a-duration", "20s"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requireMargin("30s", "not-a-duration"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requireMargin("20s", "20s")).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> requireMargin("10s", "20s")).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> requireMargin("30s", "40s")).isInstanceOf(AssertionError.class);
        requireMargin("1m", "500ms");
    }

    private static void requireMargin(Object stopGrace, Object phaseBudget) {
        assertThat(requiredDuration(stopGrace, "services.app.stop_grace_period"))
                .isGreaterThan(requiredDuration(phaseBudget, "spring.lifecycle.timeout-per-shutdown-phase"));
    }

    private static Duration requiredDuration(Object value, String key) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Missing duration: " + key);
        }
        return DurationStyle.detectAndParse(text);
    }

    private static Map<?, ?> mapping(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<?, ?>) value;
    }
}
