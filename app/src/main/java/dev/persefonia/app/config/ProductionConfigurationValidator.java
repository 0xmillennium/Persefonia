package dev.persefonia.app.config;

import dev.persefonia.app.communication.mail.ContactMailNotificationProperties;
import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitProperties;
import java.util.ArrayList;
import java.util.List;
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
}
