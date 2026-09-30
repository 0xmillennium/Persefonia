package dev.persefonia.app.medialibrary.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class LocalMediaStorageConfigurationTest {
    @Test
    void localMediaUsesIgnoredWorkspaceStorageIndependentOfContainerRecreation() throws Exception {
        Map<?, ?> compose = new Yaml().load(Files.readString(Path.of("../compose.yaml")));
        Map<?, ?> app = (Map<?, ?>) ((Map<?, ?>) compose.get("services")).get("app");
        Map<?, ?> environment = (Map<?, ?>) app.get("environment");
        assertThat(environment.get("PERSEFONIA_MEDIA_STORAGE_ROOT")).isEqualTo("/workspace/.runtime/media");
        assertThat(environment.get("PERSEFONIA_MEDIA_STORAGE_REQUIRED")).isEqualTo("true");
        assertThat(Files.readString(Path.of("../.env.example"))).contains(".runtime/ keeps build caches and local media");
        assertThat(Files.readString(Path.of("../.gitignore"))).contains(".runtime/");
    }
}
