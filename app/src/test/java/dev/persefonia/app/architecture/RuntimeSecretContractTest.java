package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class RuntimeSecretContractTest {
    private static final List<String> SECRET_NAMES = List.of("postgres_password", "redis_password",
            "contact_rate_limit_secret", "oidc_client_secret", "cloudflare_api_token");

    @Test
    void productionFileSecretsDoNotRelyOnIgnoredUidGidOrModeRemapping() throws Exception {
        Map<?, ?> compose = new Yaml().load(Files.readString(Path.of("../compose.production.yaml")));
        Map<?, ?> services = (Map<?, ?>) compose.get("services");
        for (Object service : services.values()) {
            List<?> secrets = (List<?>) ((Map<?, ?>) service).get("secrets");
            for (Object secret : secrets) {
                if (secret instanceof Map<?, ?> entry) {
                    for (String permission : List.of("uid", "gid", "mode")) {
                        assertThat(entry.containsKey(permission)).as("file-secret %s remapping", permission).isFalse();
                    }
                } else {
                    assertThat(secret).isInstanceOf(String.class);
                }
            }
        }
    }

    @Test
    void flatExamplesAreSourceAndComposeUsesOnlyRealSecretNames() throws Exception {
        String compose = Files.readString(Path.of("../compose.production.yaml"));
        Map<?, ?> secrets = (Map<?, ?>) ((Map<?, ?>) new Yaml().load(compose)).get("secrets");
        for (String name : SECRET_NAMES) {
            assertThat(Path.of("../secrets/" + name + ".examples")).isRegularFile();
            assertThat(((Map<?, ?>) secrets.get(name)).get("file")).isEqualTo("./secrets/" + name);
        }
        assertThat(compose).doesNotContain(".examples", "secrets/examples/");
        assertThat(Files.readString(Path.of("../.gitignore"))).doesNotContain("secrets/*", "secrets/**");
    }

    @Test
    void localEnvironmentPathsUseExtensionlessNamesAndComposeConsumesThoseVariables() throws Exception {
        Properties environmentExample = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("../.env.example"))) {
            environmentExample.load(reader);
        }
        Map<?, ?> secrets = (Map<?, ?>) ((Map<?, ?>) new Yaml()
                .load(Files.readString(Path.of("../compose.yaml")))).get("secrets");
        for (var entry : Map.of(
                "postgres_password", "PERSEFONIA_POSTGRES_PASSWORD_FILE",
                "redis_password", "PERSEFONIA_REDIS_PASSWORD_FILE",
                "contact_rate_limit_secret", "PERSEFONIA_CONTACT_RATE_LIMIT_SECRET_FILE").entrySet()) {
            assertThat(environmentExample.getProperty(entry.getValue())).isEqualTo("./secrets/" + entry.getKey());
            assertThat(((Map<?, ?>) secrets.get(entry.getKey())).get("file").toString())
                    .startsWith("${" + entry.getValue() + ":?").endsWith("}");
        }
    }

    @Test
    void activeRuntimeDescriptorsAndEnvironmentExamplesRejectObsoleteSecretFilenames() throws Exception {
        for (String file : List.of("compose.yaml", "compose.production.yaml", ".env.example", ".env.production.example")) {
            String contents = Files.readString(Path.of("../" + file));
            for (String name : SECRET_NAMES) {
                assertThat(contents).as("%s secret paths", file).doesNotContain(name + ".txt");
            }
        }
    }

    @Test
    void productionRuntimeBindSourcesExistAndStartupHelperIsExecutable() throws Exception {
        for (String file : List.of("docker/postgresql/postgresql.conf", "docker/postgresql/pg_hba.conf",
                "docker/redis/redis.conf", "docker/redis-start.sh")) {
            assertThat(Path.of("../" + file)).isRegularFile();
            assertThat(Files.readString(Path.of("../compose.production.yaml"))).contains("source: ./" + file);
        }
        assertThat(Files.isExecutable(Path.of("../docker/redis-start.sh"))).isTrue();
        String redis = Files.readString(Path.of("../docker/redis-start.sh"));
        assertThat(redis).contains("user default reset on nopass -@all +ping", "~%s:* +@connection +incr +expire +pexpire");
    }
}
