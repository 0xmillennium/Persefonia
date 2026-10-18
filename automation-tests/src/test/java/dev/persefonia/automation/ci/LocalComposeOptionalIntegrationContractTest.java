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
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

class LocalComposeOptionalIntegrationContractTest {
    private static final String START = "# BEGIN SHARED OPTIONAL INTEGRATION SCAFFOLD";
    private static final String END = "# END SHARED OPTIONAL INTEGRATION SCAFFOLD";

    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();
    private final Path example = repository.resolve("compose.override.example.yaml");

    @Test
    void untouchedExampleDoesNotActivateIntegrations() throws Exception {
        Path environment = environment(false);
        Path base = temporary.resolve("base.json");
        Path inert = temporary.resolve("inert.json");
        Files.writeString(base, render(environment));
        Files.writeString(inert, render(environment, example));
        String meaningful = "{services, secrets, volumes, networks}";
        assertThat(jq(meaningful, base).stdout()).isEqualTo(jq(meaningful, inert).stdout());
    }

    @Test
    void sharedScaffoldCombinesOidcCloudflareAndSmtpWithoutDuplicateKeys() throws Exception {
        String source = Files.readString(example);
        assertThat(source).contains("Copy to ignored compose.override.yaml", START, END,
                "SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_AUTHELIA_ISSUER_URI",
                "spring.security.oauth2.client.registration.authelia.client-secret",
                "PERSEFONIA_CACHE_PURGE_PROVIDER", "persefonia.cache-purge.cloudflare.api-token",
                "PERSEFONIA_SMTP_HOST", "PERSEFONIA_SMTP_PASSWORD_FILE", "spring.mail.password",
                "SERVER_FORWARD_HEADERS_STRATEGY", "SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES");
        for (String scaffold : new String[] {"#    environment:", "#    secrets:", "#secrets:"}) {
            assertThat(source.lines().filter(scaffold::equals).count()).as(scaffold).isEqualTo(1);
        }

        String enabled = enableAll(source);
        new Load(LoadSettings.builder().setAllowDuplicateKeys(false).build()).loadFromString(enabled);
        Path override = temporary.resolve("combined.override.yaml");
        Files.writeString(override, enabled);
        Path rendered = temporary.resolve("combined.json");
        String configuration = render(environment(true), override);
        Files.writeString(rendered, configuration);
        for (String name : new String[] {"oidc_client_secret", "cloudflare_api_token", "smtp_password"}) {
            String fakeCredential = Files.readString(repository.resolve("secrets/" + name + ".examples")).trim();
            assertThat(fakeCredential).isNotBlank();
            assertThat(configuration).doesNotContain(fakeCredential);
        }
        String contract = """
                (.services | keys) == ["app", "postgres", "redis"]
                and (.services.app.environment | has("SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_AUTHELIA_ISSUER_URI"))
                and (.services.app.environment | has("PERSEFONIA_CACHE_PURGE_PROVIDER"))
                and (.services.app.environment | has("PERSEFONIA_SMTP_HOST"))
                and (.services.app.environment | has("SERVER_FORWARD_HEADERS_STRATEGY"))
                and (.services.app.secrets | map(.source) | index("oidc_client_secret") != null)
                and (.services.app.secrets | map(.source) | index("cloudflare_api_token") != null)
                and (.services.app.secrets | map(.source) | index("smtp_password") != null)
                and (.services.app.secrets | any(.source == "oidc_client_secret" and .target == "spring.security.oauth2.client.registration.authelia.client-secret"))
                and (.services.app.secrets | any(.source == "cloudflare_api_token" and .target == "persefonia.cache-purge.cloudflare.api-token"))
                and (.services.app.secrets | any(.source == "smtp_password" and .target == "spring.mail.password"))
                and (.secrets | has("oidc_client_secret") and has("cloudflare_api_token") and has("smtp_password"))
                and (.secrets.oidc_client_secret.file | endswith("/secrets/oidc_client_secret.examples"))
                and (.secrets.cloudflare_api_token.file | endswith("/secrets/cloudflare_api_token.examples"))
                and (.secrets.smtp_password.file | endswith("/secrets/smtp_password.examples"))
                and (.services.app.environment | keys | all(.[]; (test("(PASSWORD|SECRET|TOKEN|API_KEY)$") | not)))
                """;
        assertThat(jq(contract, rendered).stdout().trim()).isEqualTo("true");
    }

    private String enableAll(String source) {
        assertThat(source).contains("  app: {}", START, END);
        StringBuilder enabled = new StringBuilder();
        boolean inside = false;
        for (String line : source.split("\\R", -1)) {
            if (line.equals(START)) {
                inside = true;
            } else if (line.equals(END)) {
                inside = false;
            } else if (inside && line.startsWith("#")) {
                enabled.append(line.substring(1)).append('\n');
            } else {
                enabled.append(line).append('\n');
            }
        }
        return enabled.toString().replace("  app: {}", "  app:");
    }

    private Path environment(boolean integrations) throws Exception {
        Path path = temporary.resolve(integrations ? "combined.env" : "baseline.env");
        String values = Files.readString(repository.resolve(".env.example"));
        if (integrations) {
            values += """

                    PERSEFONIA_OIDC_ISSUER_URI=https://id.example.invalid
                    PERSEFONIA_OIDC_CLIENT_ID=persefonia-local
                    PERSEFONIA_CLOUDFLARE_ZONE_ID=fake-zone
                    PERSEFONIA_SMTP_HOST=mail.example.invalid
                    PERSEFONIA_SMTP_USERNAME=fake-user
                    PERSEFONIA_CONTACT_MAIL_OWNER_RECIPIENT=owner@example.invalid
                    PERSEFONIA_CONTACT_MAIL_FROM=site@example.invalid
                    PERSEFONIA_TRUSTED_PROXY_CIDRS=127.0.0.1/32
                    PERSEFONIA_OIDC_CLIENT_SECRET_FILE=%s
                    PERSEFONIA_CLOUDFLARE_API_TOKEN_FILE=%s
                    PERSEFONIA_SMTP_PASSWORD_FILE=%s
                    """.formatted(
                    repository.resolve("secrets/oidc_client_secret.examples"),
                    repository.resolve("secrets/cloudflare_api_token.examples"),
                    repository.resolve("secrets/smtp_password.examples"));
        }
        Files.writeString(path, values);
        return path;
    }

    private String render(Path environment, Path... overrides) throws Exception {
        var arguments = new java.util.ArrayList<>(java.util.List.of(
                "docker", "compose", "--env-file", environment.toString(), "-f",
                repository.resolve("compose.yaml").toString()));
        for (Path override : overrides) {
            arguments.add("-f");
            arguments.add(override.toString());
        }
        arguments.addAll(java.util.List.of("config", "--format", "json"));
        CommandResult result = new CommandRunner(repository, temporary, Duration.ofSeconds(30),
                Map.of("COMPOSE_DISABLE_ENV_FILE", "1", "HOME", System.getProperty("user.home")))
                .run(arguments);
        result.requireSuccess();
        return result.stdout();
    }

    private CommandResult jq(String filter, Path input) throws Exception {
        CommandResult result = new CommandRunner(repository, temporary, Duration.ofSeconds(10), Map.of())
                .run("jq", "-e", filter, input.toString());
        result.requireSuccess();
        return result;
    }
}
