package dev.persefonia.app.platformoperations.cache.config;

import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "persefonia.cache-purge")
public class CachePurgeProperties {
    private static final Duration MIN_STRANDED_AFTER = Duration.ofMinutes(1);
    private static final Duration MAX_STRANDED_AFTER = Duration.ofHours(24);
    private CachePurgeProvider provider = CachePurgeProvider.LOCAL;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
    private Duration strandedAfter = Duration.ofMinutes(15);
    private final Cloudflare cloudflare = new Cloudflare();

    public CachePurgeProvider getProvider() { return provider; }
    public void setProvider(CachePurgeProvider provider) { this.provider = provider; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Duration getStrandedAfter() { return strandedAfter; }
    public void setStrandedAfter(Duration strandedAfter) {
        if (strandedAfter == null || strandedAfter.compareTo(MIN_STRANDED_AFTER) < 0
                || strandedAfter.compareTo(MAX_STRANDED_AFTER) > 0) {
            throw new IllegalArgumentException("cache purge stranded-after must be between 1 minute and 24 hours");
        }
        this.strandedAfter = strandedAfter;
    }
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
