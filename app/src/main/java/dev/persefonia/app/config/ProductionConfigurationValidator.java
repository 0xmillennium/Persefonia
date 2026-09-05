package dev.persefonia.app.config;

import dev.persefonia.app.communication.mail.ContactMailNotificationProperties;
import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Fails fast at startup when a production deployment still carries
 * local-friendly defaults. It activates only under the explicit
 * {@code prod}/{@code production} profile, so local development and the many
 * test contexts (which run under other profiles) stay convenient.
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod | production")
class ProductionConfigurationValidator implements InitializingBean {
    static final String LOCAL_RATE_LIMIT_SECRET_DEFAULT =
            "local-development-contact-rate-limit-secret-change-me";
    static final int MINIMUM_RATE_LIMIT_SECRET_LENGTH = 32;
    static final String CONTACT_MAIL_REQUIRED_PROPERTY =
            "persefonia.contact.mail.require-in-production";
    private static final String TRUSTED_PROXY_SENTINEL = "127.0.0.1/32";
    private static final Set<String> ACTUATOR_ENDPOINTS =
            Set.of("health", "info", "metrics", "prometheus");

    private final Environment environment;
    private final ContactRateLimitProperties rateLimitProperties;
    private final ContactMailNotificationProperties mailProperties;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    ProductionConfigurationValidator(
            Environment environment,
            ContactRateLimitProperties rateLimitProperties,
            ContactMailNotificationProperties mailProperties,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.environment = environment;
        this.rateLimitProperties = rateLimitProperties;
        this.mailProperties = mailProperties;
        this.clientRegistrations = clientRegistrations;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> violations = new ArrayList<>();
        validateSessionCookie(violations);
        validateRateLimitSecret(violations);
        validatePublicBaseUrl(violations);
        validateAdminOidc(violations);
        validateContactMail(violations);
        validateForwardedHeaders(violations);
        validateManagementIsolation(violations);
        validateActuatorExposure(violations);
        validateRequiredMedia(violations);

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start with unsafe production configuration: "
                            + String.join("; ", violations));
        }
    }

    private void validateSessionCookie(List<String> violations) {
        boolean secure = environment.getProperty(
                "server.servlet.session.cookie.secure", Boolean.class, false);
        if (!secure) {
            violations.add("session cookie must be marked secure in production");
        }
    }

    private void validateRateLimitSecret(List<String> violations) {
        String secret = rateLimitProperties.secret();
        if (secret == null || secret.isBlank()) {
            violations.add("contact rate-limit secret must be configured in production");
            return;
        }
        if (LOCAL_RATE_LIMIT_SECRET_DEFAULT.equals(secret)) {
            violations.add("contact rate-limit secret must not use the local development default");
        }
        if (secret.length() < MINIMUM_RATE_LIMIT_SECRET_LENGTH) {
            violations.add("contact rate-limit secret must be at least "
                    + MINIMUM_RATE_LIMIT_SECRET_LENGTH + " characters in production");
        }
    }

    private void validatePublicBaseUrl(List<String> violations) {
        String publicBaseUrl = environment.getProperty("site.public-base-url");
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            violations.add("public base URL must be configured in production");
            return;
        }
        if (!publicBaseUrl.startsWith("https://")) {
            violations.add("public base URL must use HTTPS in production");
        }
    }

    private void validateAdminOidc(List<String> violations) {
        if (clientRegistrations.getIfAvailable() == null) {
            violations.add("admin OIDC client registration must be configured in production");
        }
    }

    private void validateContactMail(List<String> violations) {
        boolean requiredInProduction = environment.getProperty(
                CONTACT_MAIL_REQUIRED_PROPERTY, Boolean.class, true);
        if (requiredInProduction && !mailProperties.enabled()) {
            violations.add("contact mail must be enabled in production (set "
                    + CONTACT_MAIL_REQUIRED_PROPERTY + "=false to opt out)");
        }
        if (mailProperties.enabled()) {
            if (mailProperties.ownerRecipient() == null) {
                violations.add("contact mail owner recipient must be configured when mail is enabled");
            }
            if (mailProperties.from() == null) {
                violations.add("contact mail from address must be configured when mail is enabled");
            }
        }
    }

    private void validateForwardedHeaders(List<String> violations) {
        String strategy = environment.getProperty("server.forward-headers-strategy");
        if (!"NATIVE".equalsIgnoreCase(strategy)) {
            violations.add("forwarded-header strategy must be NATIVE in production");
        }
        requireNonBlank("server.tomcat.remoteip.remote-ip-header", "remote IP header", violations);
        requireNonBlank("server.tomcat.remoteip.protocol-header", "protocol header", violations);

        String trustedProxies = environment.getProperty("server.tomcat.remoteip.internal-proxies");
        if (isUnsafeTrustedProxyValue(trustedProxies)) {
            violations.add("trusted proxy ranges must be explicitly configured and must not trust all proxies");
        }
    }

    private void validateManagementIsolation(List<String> violations) {
        String address = environment.getProperty("management.server.address", "127.0.0.1");
        if (!isLoopbackAddress(address)) {
            violations.add("management server address must be loopback in production");
        }
        int applicationPort = environment.getProperty("server.port", Integer.class, 8080);
        int managementPort = environment.getProperty("management.server.port", Integer.class, 9001);
        if (applicationPort == managementPort) {
            violations.add("management server port must differ from the application port");
        }
    }

    private void validateActuatorExposure(List<String> violations) {
        String configured = environment.getProperty("management.endpoints.web.exposure.include", "");
        Set<String> endpoints = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (endpoints.contains("*") || !ACTUATOR_ENDPOINTS.containsAll(endpoints)
                || !endpoints.equals(ACTUATOR_ENDPOINTS)) {
            violations.add("Actuator exposure must be limited to health, info, metrics, and prometheus in production");
        }
    }

    private void validateRequiredMedia(List<String> violations) {
        if (!environment.getProperty("persefonia.media.storage-required", Boolean.class, false)) {
            violations.add("media storage must be required in production");
        }
    }

    private void requireNonBlank(String property, String label, List<String> violations) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            violations.add(label + " must be configured in production");
        }
    }

    private static boolean isLoopbackAddress(String address) {
        return "127.0.0.1".equals(address) || "::1".equals(address)
                || "0:0:0:0:0:0:0:1".equals(address);
    }

    private static boolean isUnsafeTrustedProxyValue(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim();
        return TRUSTED_PROXY_SENTINEL.equals(normalized)
                || "*".equals(normalized)
                || ".*".equals(normalized)
                || "0.0.0.0/0".equals(normalized)
                || "::/0".equals(normalized);
    }
}
