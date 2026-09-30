package dev.persefonia.automation.ci;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.CommandResult;
import dev.persefonia.automation.support.CommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalComposePolicyContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();
    private final Path valid = repository.resolve("automation-tests/src/test/resources/compose/valid-local-development.json");

    @Test
    void acceptsTheThreeServiceSourceRuntime() throws Exception {
        policy(valid).requireSuccess();
    }

    @Test
    void rejectsLocalRuntimeAndProductionBoundaryRegressions() throws Exception {
        Map<String, String> invalid = Map.ofEntries(
                Map.entry("project name", ".name = \"persefonia\""),
                Map.entry("extra service", ".services.traefik = {}"),
                Map.entry("missing redis", "del(.services.redis)"),
                Map.entry("application build", ".services.app.build = \".\""),
                Map.entry("mutable image", ".services.redis.image = \"redis:8-alpine\""),
                Map.entry("release image", ".services.app.image = \"ghcr.io/0xmillennium/persefonia@sha256:\" + (\"a\" * 64)"),
                Map.entry("root user", ".services.app.user = \"0:0\""),
                Map.entry("writable root", ".services.app.read_only = false"),
                Map.entry("privileged", ".services.app.privileged = true"),
                Map.entry("wrong source", ".services.app.volumes[0].source = \"/other\""),
                Map.entry("image command", ".services.app.command = [\"java\", \"-jar\", \"app.jar\"]"),
                Map.entry("docker profile", ".services.app.environment.SPRING_PROFILES_ACTIVE = \"docker\""),
                Map.entry("implicit env", ".services.app.env_file = [\".env\"]"),
                Map.entry("wrong database", ".services.app.environment.PERSEFONIA_DATASOURCE_URL = \"jdbc:postgresql://other/db\""),
                Map.entry("app password environment", ".services.app.environment.SPRING_DATA_REDIS_PASSWORD = \"unsafe\""),
                Map.entry("postgres password environment", ".services.postgres.environment.POSTGRES_PASSWORD = \"unsafe\""),
                Map.entry("redis password environment", ".services.redis.environment.REDIS_PASSWORD = \"unsafe\""),
                Map.entry("missing app database secret", "del(.services.app.secrets[0])"),
                Map.entry("wrong app Redis secret target", ".services.app.secrets[1].target = \"other\""),
                Map.entry("wrong contact secret target", ".services.app.secrets[2].target = \"other\""),
                Map.entry("missing postgres file binding", "del(.services.postgres.environment.POSTGRES_PASSWORD_FILE)"),
                Map.entry("default Redis identity", ".services.app.environment.SPRING_DATA_REDIS_USERNAME = \"default\""),
                Map.entry("Redis prefix mismatch", ".services.redis.environment.PERSEFONIA_REDIS_KEY_PREFIX = \"other\""),
                Map.entry("unhealthy dependency", ".services.app.depends_on.redis.condition = \"service_started\""),
                Map.entry("missing readiness", ".services.app.healthcheck.test = []"),
                Map.entry("public app port", ".services.app.ports[0].host_ip = \"0.0.0.0\""),
                Map.entry("public database", ".services.postgres.ports[0].host_ip = \"0.0.0.0\""),
                Map.entry("postgres data removed", ".services.postgres.volumes = []"),
                Map.entry("redis persisted", ".services.redis.volumes += [{type: \"volume\", source: \"redis-data\", target: \"/data\"}]"),
                Map.entry("redis unauthenticated", ".services.redis.entrypoint = [\"redis-server\"]"),
                Map.entry("redis root", ".services.redis.user = \"0:0\""),
                Map.entry("redis secret missing", "del(.services.redis.secrets)"),
                Map.entry("redis ACL source removed", ".services.redis.volumes = []"),
                Map.entry("secret values in source", ".secrets.redis_password.file = \"/tmp/redis-password\""),
                Map.entry("swapped secret source", ".secrets.redis_password.file = \"/synthetic/workspace/secrets/postgres_password.examples\""),
                Map.entry("custom network", ".networks.production = {}"),
                Map.entry("proxy route", ".services.app.labels = {\"traefik.enable\": \"true\"}"));
        for (var entry : invalid.entrySet()) {
            Path variant = temporary.resolve(entry.getKey().replace(' ', '-') + ".json");
            CommandResult mutation = runner().run("jq", entry.getValue(), valid.toString());
            mutation.requireSuccess();
            Files.writeString(variant, mutation.stdout());
            assertThat(policy(variant).status()).as(entry.getKey()).isNotZero();
        }
    }

    private CommandRunner runner() {
        return new CommandRunner(repository, temporary, Duration.ofSeconds(10), Map.of());
    }

    private CommandResult policy(Path input) throws Exception {
        return runner().run("jq", "-e", "--arg", "workspace", "/synthetic/workspace",
                "-f", repository.resolve("scripts/ci/local-compose-policy.jq").toString(), input.toString());
    }
}
