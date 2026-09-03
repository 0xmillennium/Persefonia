package dev.persefonia.app.platformoperations.cache.config;

import dev.persefonia.app.platformoperations.cache.provider.CloudflareCachePurgeAdapter;
import dev.persefonia.app.platformoperations.cache.provider.LocalCachePurgeAdapter;
import dev.persefonia.platformoperations.application.cache.CachePurgePort;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CachePurgeProperties.class)
public class CachePurgeProviderConfiguration {
    private static final Pattern ZONE_ID = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    CachePurgePort cachePurgePort(
            CachePurgeProperties properties,
            @Value("${site.public-base-url}") String publicBaseUrl) {
        Objects.requireNonNull(properties, "properties");
        if (properties.getProvider() == CachePurgeProvider.LOCAL) {
            return new LocalCachePurgeAdapter();
        }
        if (properties.getProvider() != CachePurgeProvider.CLOUDFLARE) {
            throw new IllegalStateException("Unsupported public edge cache purge provider.");
        }

        validateTimeout(properties.getConnectTimeout(), "connect");
        validateTimeout(properties.getReadTimeout(), "read");
        String zoneId = properties.getCloudflare().getZoneId();
        if (zoneId == null || !ZONE_ID.matcher(zoneId).matches()) {
            throw new IllegalStateException(
                    "Cloudflare zone ID must be configured when cache purge provider is CLOUDFLARE.");
        }
        String apiToken = properties.getCloudflare().getApiToken();
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalStateException(
                    "Cloudflare API token must be configured when cache purge provider is CLOUDFLARE.");
        }
        URI publicOrigin = publicOrigin(publicBaseUrl);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        RestClient client = RestClient.builder()
                .baseUrl(CloudflareCachePurgeAdapter.API_ORIGIN)
                .requestFactory(requestFactory)
                .build();
        return new CloudflareCachePurgeAdapter(client, publicOrigin, zoneId, apiToken);
    }

    static URI publicOrigin(String configured) {
        try {
            URI uri = new URI(configured);
            String path = uri.getPath();
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getHost().isBlank() || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || path != null && !path.isEmpty() && !path.equals("/")) {
                throw invalidPublicOrigin();
            }
            return new URI("https", null, uri.getHost(), uri.getPort(), null, null, null);
        } catch (URISyntaxException | NullPointerException invalidUri) {
            throw invalidPublicOrigin();
        }
    }

    private static void validateTimeout(Duration timeout, String name) {
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalStateException("Cloudflare " + name + " timeout must be greater than zero and at most 30 seconds.");
        }
    }

    private static IllegalStateException invalidPublicOrigin() {
        return new IllegalStateException(
                "site.public-base-url must be an HTTPS origin when cache purge provider is CLOUDFLARE.");
    }
}
