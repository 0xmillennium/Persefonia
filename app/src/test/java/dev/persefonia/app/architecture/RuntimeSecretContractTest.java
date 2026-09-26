package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class RuntimeSecretContractTest {
    @Test
    void flatExamplesAreSourceAndComposeUsesOnlyRealSecretNames() throws Exception {
        String compose = Files.readString(Path.of("../compose.production.yaml"));
        for (String name : List.of("postgres_password", "redis_password", "contact_rate_limit_secret",
                "oidc_client_secret", "cloudflare_api_token")) {
            assertThat(Path.of("../secrets/" + name + ".examples")).isRegularFile();
            assertThat(compose).contains("file: ./secrets/" + name);
        }
        assertThat(compose).doesNotContain(".examples", "secrets/examples/");
        assertThat(Files.readString(Path.of("../.gitignore"))).doesNotContain("secrets/*", "secrets/**");
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
