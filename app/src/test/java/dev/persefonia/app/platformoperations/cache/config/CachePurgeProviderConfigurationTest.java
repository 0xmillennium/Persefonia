package dev.persefonia.app.platformoperations.cache.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.platformoperations.cache.provider.CloudflareCachePurgeAdapter;
import dev.persefonia.app.platformoperations.cache.provider.LocalCachePurgeAdapter;
import dev.persefonia.platformoperations.application.cache.CachePurgePort;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CachePurgeProviderConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(CachePurgeProviderConfiguration.class)
            .withPropertyValues("site.public-base-url=https://example.com");

    @Test
    void localIsDefaultAndDoesNotRequireCloudflareCredentials() {
        context.run(result -> {
            assertThat(result).hasNotFailed();
            assertThat(result).hasSingleBean(CachePurgePort.class);
            assertThat(result.getBean(CachePurgePort.class)).isInstanceOf(LocalCachePurgeAdapter.class);
        });
    }

    @Test
    void validCloudflareConfigurationSelectsOnlyCloudflare() {
        cloudflareContext().run(result -> {
            assertThat(result).hasNotFailed();
            assertThat(result).hasSingleBean(CachePurgePort.class);
            assertThat(result.getBean(CachePurgePort.class)).isInstanceOf(CloudflareCachePurgeAdapter.class);
        });
    }

    @Test
    void cloudflareFailsFastForMissingOrInvalidCredentials() {
        context.withPropertyValues("persefonia.cache-purge.provider=CLOUDFLARE")
                .run(result -> assertThat(result).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("zone ID must be configured"));
        cloudflareContext().withPropertyValues("persefonia.cache-purge.cloudflare.api-token=")
                .run(result -> assertThat(result).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("API token must be configured"));
    }

    @Test
    void cloudflareRequiresAnHttpsRootOriginAndBoundedPositiveTimeouts() {
        cloudflareContext().withPropertyValues("site.public-base-url=http://example.com")
                .run(result -> assertThat(result).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("HTTPS origin"));
        cloudflareContext().withPropertyValues("site.public-base-url=https://example.com/application")
                .run(result -> assertThat(result).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("HTTPS origin"));
        cloudflareContext().withPropertyValues("persefonia.cache-purge.connect-timeout=0s")
                .run(result -> assertThat(result).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("connect timeout"));
        cloudflareContext().withPropertyValues("persefonia.cache-purge.read-timeout=31s")
                .run(result -> assertThat(result).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("read timeout"));
    }

    @Test
    void secretHoldingConfigurationObjectsDoNotRenderSecretValues() {
        CachePurgeProperties properties = new CachePurgeProperties();
        properties.getCloudflare().setApiToken("must-not-render");

        assertThat(properties.toString()).doesNotContain("must-not-render");
        assertThat(properties.getCloudflare().toString()).doesNotContain("must-not-render");
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    private ApplicationContextRunner cloudflareContext() {
        return context.withPropertyValues(
                "persefonia.cache-purge.provider=CLOUDFLARE",
                "persefonia.cache-purge.cloudflare.zone-id=0123456789abcdef0123456789abcdef",
                "persefonia.cache-purge.cloudflare.api-token=test-token");
    }
}
