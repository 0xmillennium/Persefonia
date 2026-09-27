package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class RuntimeSecretContractTest {
    private static final List<Secret> SECRETS = List.of(
            new Secret("postgres_password", "PERSEFONIA_POSTGRES_PASSWORD_FILE", "spring.datasource.password"),
            new Secret("redis_password", "PERSEFONIA_REDIS_PASSWORD_FILE", "spring.data.redis.password"),
            new Secret("contact_rate_limit_secret", "PERSEFONIA_CONTACT_RATE_LIMIT_SECRET_FILE",
                    "persefonia.contact.rate-limit.secret"),
            new Secret("oidc_client_secret", "PERSEFONIA_OIDC_CLIENT_SECRET_FILE",
                    "spring.security.oauth2.client.registration.authelia.client-secret"),
            new Secret("cloudflare_api_token", "PERSEFONIA_CLOUDFLARE_API_TOKEN_FILE",
                    "persefonia.cache-purge.cloudflare.api-token"));

    @Test
    void applicationCredentialExamplesRemainTrackedAndRealSecretsStayIgnoredAndUntracked() throws Exception {
        for (Secret secret : SECRETS) {
            String name = secret.name();
            String real = "secrets/" + name;
            String example = real + ".examples";
            assertThat(Path.of("../" + example)).isRegularFile();
            assertThat(git("ls-files", "--", example).output()).isEqualTo(example + "\n");
            assertThat(git("check-ignore", "-q", real).status()).isZero();
            assertThat(git("check-ignore", "-q", example).status()).isEqualTo(1);
            assertThat(git("ls-files", "--", real).output()).isEmpty();
        }
        assertThat(git("check-ignore", "-q", ".env.production").status()).isZero();
    }

    @Test
    void applicationFileSecretsPreserveTargetsWithoutPermissionRemapping() throws Exception {
        Map<?, ?> compose = new Yaml().load(Files.readString(Path.of("../compose.yaml")));
        Map<?, ?> services = (Map<?, ?>) compose.get("services");
        Map<?, ?> app = (Map<?, ?>) services.get("app");
        assertThat(app.get("secrets")).isEqualTo(SECRETS.stream()
                .map(secret -> Map.of("source", secret.name(), "target", secret.target())).toList());
    }

    @Test
    void environmentUsesExtensionlessRealSecretNamesAndComposeConsumesOnlyThoseInputs() throws Exception {
        Properties environmentExample = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("../.env.example"))) {
            environmentExample.load(reader);
        }
        Map<?, ?> secrets = (Map<?, ?>) ((Map<?, ?>) new Yaml()
                .load(Files.readString(Path.of("../compose.yaml")))).get("secrets");
        assertThat(secrets.keySet().stream().map(Object::toString).toList())
                .containsExactlyInAnyOrderElementsOf(SECRETS.stream().map(Secret::name).toList());
        for (Secret secret : SECRETS) {
            assertThat(environmentExample.getProperty(secret.variable())).isEqualTo("./secrets/" + secret.name());
            assertThat(((Map<?, ?>) secrets.get(secret.name())).get("file").toString())
                    .startsWith("${" + secret.variable() + ":?").endsWith("}");
        }
    }

    @Test
    void activeApplicationDescriptorsNeverUseExamplesOrObsoleteSecretFilenames() throws Exception {
        for (String file : List.of("../compose.yaml", "../.env.example",
                "src/main/resources/application-docker.yml", "src/main/resources/application-prod.yml")) {
            String contents = Files.readString(Path.of(file));
            assertThat(contents).as(file).doesNotContain(".examples", "secrets/examples/");
            for (Secret secret : SECRETS) {
                assertThat(contents).as("%s secret paths", file).doesNotContain(secret.name() + ".txt");
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
    private record Secret(String name, String variable, String target) {}
}
