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
    void defaultLocalComposeRequiresNoSecretFilesAndLocalProfileKeepsConfigtreeOptional() throws Exception {
        Map<?, ?> compose = new Yaml().load(Files.readString(Path.of("../compose.yaml")));
        Map<?, ?> app = (Map<?, ?>) ((Map<?, ?>) compose.get("services")).get("app");
        assertThat(compose.containsKey("secrets")).isFalse();
        assertThat(app.containsKey("secrets")).isFalse();
        assertThat(app.containsKey("env_file")).isFalse();
        assertThat(Files.readString(Path.of("src/main/resources/application-local.yml")))
                .contains("optional:configtree:/run/secrets/");
        assertThat(Files.readString(Path.of("src/main/resources/application-docker.yml")))
                .contains("configtree:/run/secrets/");
    }

    @Test
    void activeApplicationDescriptorsNeverUseExamplesOrObsoleteSecretFilenames() throws Exception {
        for (String file : List.of("../compose.yaml", "../.env.example",
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
