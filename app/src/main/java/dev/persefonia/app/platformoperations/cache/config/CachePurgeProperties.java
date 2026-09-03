package dev.persefonia.app.platformoperations.cache.config;

import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "persefonia.cache-purge")
public class CachePurgeProperties {
    private CachePurgeProvider provider = CachePurgeProvider.LOCAL;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
    private final Cloudflare cloudflare = new Cloudflare();

    public CachePurgeProvider getProvider() { return provider; }
    public void setProvider(CachePurgeProvider provider) { this.provider = provider; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Cloudflare getCloudflare() { return cloudflare; }

    public static final class Cloudflare {
        private String zoneId;
        private String apiToken;

        public String getZoneId() { return zoneId; }
        public void setZoneId(String zoneId) { this.zoneId = zoneId; }
        public String getApiToken() { return apiToken; }
        public void setApiToken(String apiToken) { this.apiToken = apiToken; }
    }
}
