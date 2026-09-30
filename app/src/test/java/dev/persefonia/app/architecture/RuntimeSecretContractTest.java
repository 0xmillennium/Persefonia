package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class RuntimeSecretContractTest {
    private static final List<String> SECRETS = List.of(
            "postgres_password", "redis_password", "contact_rate_limit_secret",
            "oidc_client_secret", "cloudflare_api_token");

    @Test
    void applicationCredentialExamplesRemainTrackedAndRealSecretsStayIgnoredAndUntracked() throws Exception {
        for (String name : SECRETS) {
            String real = "secrets/" + name;
            String example = real + ".examples";
            assertThat(Path.of("../" + example)).isRegularFile();
            String exampleValue = Files.readString(Path.of("../" + example)).trim();
            assertThat(exampleValue).isNotBlank().startsWith("fake").doesNotContain("<", ">", "REQUIRED", "TODO");
            assertThat(git("ls-files", "--", example).output()).isEqualTo(example + "\n");
            assertThat(git("check-ignore", "-q", real).status()).isZero();
            assertThat(git("check-ignore", "-q", example).status()).isEqualTo(1);
            assertThat(git("ls-files", "--", real).output()).isEmpty();
        }
        for (String local : List.of(".env", ".env.production", "compose.override.yaml", "compose.override.yml",
                "secrets/another_real_secret")) {
            assertThat(git("check-ignore", "-q", local).status()).as(local).isZero();
        }
    }

    @Test
    void defaultLocalComposeUsesTrackedFileSecretsAndLocalProfileKeepsConfigtreeOptional() throws Exception {
        Map<?, ?> compose = new Yaml().load(Files.readString(Path.of("../compose.yaml")));
        Map<?, ?> services = (Map<?, ?>) compose.get("services");
        Map<?, ?> app = (Map<?, ?>) services.get("app");
        Map<?, ?> postgres = (Map<?, ?>) services.get("postgres");
        Map<?, ?> redis = (Map<?, ?>) services.get("redis");
        assertThat(((Map<?, ?>) compose.get("secrets")).keySet().stream().map(Object::toString).toList())
                .containsExactlyInAnyOrder("postgres_password", "redis_password", "contact_rate_limit_secret");
        assertThat(app.get("secrets").toString())
                .contains("spring.datasource.password", "spring.data.redis.password",
                        "persefonia.contact.rate-limit.secret");
        assertThat(postgres.get("secrets").toString()).contains("postgres_password");
        assertThat(redis.get("secrets").toString()).contains("redis_password");
        assertThat(((Map<?, ?>) postgres.get("environment")).get("POSTGRES_PASSWORD_FILE"))
                .isEqualTo("/run/secrets/postgres_password");
        for (Map<?, ?> service : List.of(app, postgres, redis)) {
            Map<?, ?> environment = (Map<?, ?>) service.get("environment");
            assertThat(environment.keySet().stream().map(Object::toString))
                    .noneMatch(key -> key.matches(".*(?:PASSWORD|SECRET|TOKEN)$"));
        }
        assertThat(app.containsKey("env_file")).isFalse();
        assertThat(Files.readString(Path.of("src/main/resources/application-local.yml")))
                .contains("optional:configtree:/run/secrets/");
        assertThat(Files.readString(Path.of("src/main/resources/application-docker.yml")))
                .contains("configtree:/run/secrets/");
    }

    @Test
    void exampleEnvironmentContainsOnlySecretPathsAndNoRuntimeCredentialValues() throws Exception {
        String environment = Files.readString(Path.of("../.env.example"));
        for (String name : List.of("POSTGRES_PASSWORD", "REDIS_PASSWORD", "PERSEFONIA_CONTACT_RATE_LIMIT_SECRET")) {
            assertThat(environment).doesNotContainPattern("(?m)^" + name + "=");
        }
        for (String name : List.of("PERSEFONIA_POSTGRES_PASSWORD_FILE", "PERSEFONIA_REDIS_PASSWORD_FILE",
                "PERSEFONIA_CONTACT_RATE_LIMIT_SECRET_FILE")) {
            assertThat(environment).containsPattern("(?m)^" + name + "=./secrets/[^\\n]+\\.examples$");
        }
        assertThat(environment).doesNotContain("PERSEFONIA_IMAGE_REF", "/var/lib/persefonia");
        for (String name : List.of("postgres_password", "redis_password", "contact_rate_limit_secret")) {
            String value = Files.readString(Path.of("../secrets/" + name + ".examples")).trim();
            assertThat(value).isNotBlank().doesNotContain("<", ">", "REQUIRED", "TODO");
            assertThat(environment).doesNotContain(value);
        }
    }

    @Test
    void productionApplicationDescriptorsNeverUseExamplesOrObsoleteSecretFilenames() throws Exception {
        for (String file : List.of(
                "src/main/resources/application-local.yml", "src/main/resources/application-docker.yml",
                "src/main/resources/application-prod.yml")) {
            String contents = Files.readString(Path.of(file));
            assertThat(contents).as(file).doesNotContain(".examples", "secrets/examples/");
            for (String name : SECRETS) {
                assertThat(contents).as("%s secret paths", file).doesNotContain(name + ".txt");
            }
        }
    }

    private static GitResult git(String... arguments) throws Exception {
        var command = new java.util.ArrayList<>(List.of("git", "-C", ".."));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int status = process.waitFor();
        assertThat(status).as(command.toString()).isBetween(0, 1);
        return new GitResult(status, output);
    }

    private record GitResult(int status, String output) {}
}
