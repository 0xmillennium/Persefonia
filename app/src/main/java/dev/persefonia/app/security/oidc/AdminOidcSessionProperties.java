package dev.persefonia.app.security.oidc;

import java.time.Duration;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "persefonia.security.admin-session")
public class AdminOidcSessionProperties implements InitializingBean {
    private Duration revalidationInterval = Duration.ofMinutes(5);
    private Duration oidcConnectTimeout = Duration.ofSeconds(2);
    private Duration oidcReadTimeout = Duration.ofSeconds(5);

    public Duration getRevalidationInterval() {
        return revalidationInterval;
    }

    public void setRevalidationInterval(Duration revalidationInterval) {
        this.revalidationInterval = revalidationInterval;
    }

    public Duration getOidcConnectTimeout() {
        return oidcConnectTimeout;
    }

    public void setOidcConnectTimeout(Duration oidcConnectTimeout) {
        this.oidcConnectTimeout = oidcConnectTimeout;
    }

    public Duration getOidcReadTimeout() {
        return oidcReadTimeout;
    }

    public void setOidcReadTimeout(Duration oidcReadTimeout) {
        this.oidcReadTimeout = oidcReadTimeout;
    }

    @Override
    public void afterPropertiesSet() {
        requirePositive(revalidationInterval, "revalidation interval");
        requireTimeout(oidcConnectTimeout, "OIDC connect timeout");
        requireTimeout(oidcReadTimeout, "OIDC read timeout");
    }

    private static void requireTimeout(Duration value, String name) {
        requirePositive(value, name);
        if (value.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException(name + " must not exceed 30 seconds");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
