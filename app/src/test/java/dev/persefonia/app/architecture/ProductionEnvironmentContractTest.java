package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionEnvironmentContractTest {
    @Test
    void localTemplateContainsOnlyStandaloneRuntimeInputs() throws Exception {
        String local = Files.readString(Path.of("../.env.example"));

        assertThat(keys(local)).contains("PERSEFONIA_IMAGE_REF", "PERSEFONIA_APP_PORT",
                "POSTGRES_PORT", "REDIS_PORT", "PERSEFONIA_MEDIA_HOST_PATH",
                "PERSEFONIA_POSTGRES_PASSWORD_FILE", "PERSEFONIA_REDIS_PASSWORD_FILE",
                "PERSEFONIA_CONTACT_RATE_LIMIT_SECRET_FILE")
                .doesNotContain("PERSEFONIA_PUBLIC_HOST", "PERSEFONIA_TRUSTED_PROXY_CIDRS",
                        "PERSEFONIA_OIDC_ISSUER_URI", "PERSEFONIA_SMTP_HOST",
                        "PERSEFONIA_TRAEFIK_NETWORK", "PERSEFONIA_ADMIN_ALLOWLISTED_SUBJECTS",
                        "PERSEFONIA_CLOUDFLARE_ZONE_ID");
    }

    @Test
    void productionTemplateContainsOnlyHostOwnedNonSecretConfiguration() throws Exception {
        String production = Files.readString(Path.of("../.env.production.example"));

        assertThat(keys(production)).contains("POSTGRES_DB", "POSTGRES_USER", "PERSEFONIA_PUBLIC_HOST",
                "PERSEFONIA_TRUSTED_PROXY_CIDRS", "PERSEFONIA_OIDC_ISSUER_URI",
                "PERSEFONIA_ADMIN_ALLOWLISTED_SUBJECTS", "PERSEFONIA_ADMIN_ALLOWLISTED_EMAILS",
                "PERSEFONIA_CONTACT_MAIL_ENABLED", "PERSEFONIA_CONTACT_MAIL_OWNER_RECIPIENT",
                "PERSEFONIA_CONTACT_MAIL_FROM", "PERSEFONIA_CLOUDFLARE_ZONE_ID")
                .doesNotContain("PERSEFONIA_IMAGE_REF", "COMPOSE_PROJECT_NAME", "PERSEFONIA_PUBLIC_BASE_URL",
                        "PERSEFONIA_OIDC_CLIENT_ID", "PERSEFONIA_SMTP_HOST", "PERSEFONIA_SMTP_PORT",
                        "PERSEFONIA_MEDIA_HOST_PATH", "PERSEFONIA_TRAEFIK_NETWORK");
        assertThat(production).doesNotContain("client_secret=", "api_token=", "PASSWORD_FILE=",
                "PRIVATE_KEY=", "postgres_password=", "redis_password=");
        String oidc = Files.readString(Path.of("src/main/resources/application-prod.yml"));
        assertThat(oidc).contains("client-authentication-method: client_secret_basic",
                "authorization-grant-type: authorization_code", "scope: openid,profile,email")
                .doesNotContain("offline_access");
    }

    private static List<String> keys(String content) {
        return content.lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.substring(0, line.indexOf('=')))
                .toList();
    }
}
