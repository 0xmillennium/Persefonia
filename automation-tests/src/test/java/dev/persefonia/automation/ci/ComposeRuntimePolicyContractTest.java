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

class ComposeRuntimePolicyContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();
    private final Path valid = repository.resolve("automation-tests/src/test/resources/compose/valid-runtime.json");

    @Test
    void acceptsTheSingleHardenedApplicationService() throws Exception {
        policy(valid).requireSuccess();
    }

    @Test
    void rejectsRuntimeOwnershipAndSecurityRegressions() throws Exception {
        Map<String, String> invalid = Map.ofEntries(
                Map.entry("extra service", ".services.postgres = {}"),
                Map.entry("build", ".services.app.build = \".\""),
                Map.entry("image", ".services.app.image = \"other\""),
                Map.entry("runtime user", ".services.app.user = \"0:0\""),
                Map.entry("writable filesystem", ".services.app.read_only = false"),
                Map.entry("capabilities", ".services.app.cap_drop = []"),
                Map.entry("security options", ".services.app.security_opt = []"),
                Map.entry("tmpfs", ".services.app.tmpfs = []"),
                Map.entry("readiness", ".services.app.healthcheck.test = []"),
                Map.entry("dependency startup", ".services.app.depends_on = {postgres: {condition: \"service_healthy\"}}"),
                Map.entry("routing labels", ".services.app.labels = {public: \"true\"}"),
                Map.entry("dependency endpoint", ".services.app.environment.PERSEFONIA_POSTGRES_HOST = \"other\""),
                Map.entry("secret set", "del(.secrets.redis_password)"),
                Map.entry("media bind", ".services.app.volumes[0].source = \"/other\""),
                Map.entry("public bind", ".services.app.ports[0].host_ip = \"0.0.0.0\""),
                Map.entry("custom volume", ".volumes.extra = {}"),
                Map.entry("custom network", ".networks.extra = {}"),
                Map.entry("production integration", ".services.app.environment.OIDC_ISSUER = \"https://issuer.invalid\""));
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
        return runner().run("jq", "-e", "--arg", "image", "example.invalid/persefonia:application",
                "--arg", "media_source", "/synthetic/media", "--arg", "secret_directory", "/synthetic/secrets",
                "--arg", "postgres_host", "postgres.example.invalid", "--arg", "postgres_port", "15432",
                "--arg", "redis_host", "redis.example.invalid", "--arg", "redis_port", "16379",
                "--arg", "redis_username", "compose-verifier", "--arg", "redis_key_prefix", "compose-verifier:rate-limit",
                "--arg", "management_port", "19001", "--arg", "app_port", "18080",
                "-f", repository.resolve("scripts/ci/compose-runtime-policy.jq").toString(), input.toString());
    }
}
