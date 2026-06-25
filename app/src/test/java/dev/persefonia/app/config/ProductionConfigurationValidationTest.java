package dev.persefonia.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.app.communication.mail.ContactMailNotificationProperties;
import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

class ProductionConfigurationValidationTest {
    private static final String STRONG_SECRET = "production-grade-contact-rate-limit-secret-value";
    private static final ClientRegistrationRepository OIDC_CONFIGURED = registrationId -> null;

    @Test
    void acceptsSecureProductionConfiguration() {
        assertThatCode(() -> validator(secureEnvironment(), strongRateLimit(), enabledMail(), OIDC_CONFIGURED)
                .afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInsecureSessionCookie() {
        MockEnvironment environment = secureEnvironment();
        environment.setProperty("server.servlet.session.cookie.secure", "false");

        assertThatThrownBy(() -> validator(environment, strongRateLimit(), enabledMail(), OIDC_CONFIGURED)
                .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("session cookie");
    }

    @Test
    void rejectsLocalDevelopmentRateLimitSecret() {
        ContactRateLimitProperties rateLimit = rateLimit(
                ProductionConfigurationValidator.LOCAL_RATE_LIMIT_SECRET_DEFAULT);

        assertThatThrownBy(() -> validator(secureEnvironment(), rateLimit, enabledMail(), OIDC_CONFIGURED)
                .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local development default");
    }

    @Test
    void rejectsShortRateLimitSecret() {
        ContactRateLimitProperties rateLimit = rateLimit("too-short-secret");

        assertThatThrownBy(() -> validator(secureEnvironment(), rateLimit, enabledMail(), OIDC_CONFIGURED)
                .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least");
    }

    @Test
    void rejectsMissingPublicBaseUrl() {
        MockEnvironment environment = secureEnvironment();
        environment.setProperty("site.public-base-url", "");

        assertThatThrownBy(() -> validator(environment, strongRateLimit(), enabledMail(), OIDC_CONFIGURED)
                .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("public base URL must be configured");
    }

    @Test
    void rejectsNonHttpsPublicBaseUrl() {
        MockEnvironment environment = secureEnvironment();
        environment.setProperty("site.public-base-url", "http://example.test");

        assertThatThrownBy(() -> validator(environment, strongRateLimit(), enabledMail(), OIDC_CONFIGURED)
                .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void rejectsMissingOidcConfiguration() {
        assertThatThrownBy(() -> validator(secureEnvironment(), strongRateLimit(), enabledMail(), null)
                .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OIDC");
    }

    @Test
    void rejectsEnabledMailWithoutRecipient() {
        ContactMailNotificationProperties mail =
                new ContactMailNotificationProperties(true, null, "from@example.test", null, null);

        assertThatThrownBy(() -> validator(secureEnvironment(), strongRateLimit(), mail, OIDC_CONFIGURED)
                .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner recipient");
    }

    @Test
    void rejectsDisabledMailUnlessExplicitlyOptedOut() {
        ContactMailNotificationProperties disabledMail =
                new ContactMailNotificationProperties(false, null, null, null, null);

        assertThatThrownBy(() -> validator(secureEnvironment(), strongRateLimit(), disabledMail, OIDC_CONFIGURED)
                .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contact mail must be enabled");
    }

    @Test
    void allowsDisabledMailWhenOptedOut() {
        MockEnvironment environment = secureEnvironment();
        environment.setProperty(
                ProductionConfigurationValidator.CONTACT_MAIL_REQUIRED_PROPERTY, "false");
        ContactMailNotificationProperties disabledMail =
                new ContactMailNotificationProperties(false, null, null, null, null);

        assertThatCode(() -> validator(environment, strongRateLimit(), disabledMail, OIDC_CONFIGURED)
                .afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void productionProfileStartupFailsWithUnsafeDefaults() {
        new ApplicationContextRunner()
                .withBean(ContactRateLimitProperties.class, ProductionConfigurationValidationTest::localRateLimit)
                .withBean(ContactMailNotificationProperties.class, ProductionConfigurationValidationTest::disabledMail)
                .withUserConfiguration(ProductionConfigurationValidator.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void localProfileStartsWithLocalDefaults() {
        new ApplicationContextRunner()
                .withBean(ContactRateLimitProperties.class, ProductionConfigurationValidationTest::localRateLimit)
                .withBean(ContactMailNotificationProperties.class, ProductionConfigurationValidationTest::disabledMail)
                .withUserConfiguration(ProductionConfigurationValidator.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void testProfileStartsWithLocalDefaults() {
        new ApplicationContextRunner()
                .withBean(ContactRateLimitProperties.class, ProductionConfigurationValidationTest::localRateLimit)
                .withBean(ContactMailNotificationProperties.class, ProductionConfigurationValidationTest::disabledMail)
                .withUserConfiguration(ProductionConfigurationValidator.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    private static ProductionConfigurationValidator validator(
            MockEnvironment environment,
            ContactRateLimitProperties rateLimit,
            ContactMailNotificationProperties mail,
            ClientRegistrationRepository clientRegistration) {
        return new ProductionConfigurationValidator(
                environment, rateLimit, mail, registrations(clientRegistration));
    }

    private static MockEnvironment secureEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("server.servlet.session.cookie.secure", "true");
        environment.setProperty("site.public-base-url", "https://example.test");
        return environment;
    }

    private static ContactRateLimitProperties strongRateLimit() {
        return rateLimit(STRONG_SECRET);
    }

    private static ContactRateLimitProperties rateLimit(String secret) {
        return new ContactRateLimitProperties(
                secret, 5, Duration.ofMinutes(15), "persefonia:rate-limit");
    }

    private static ContactRateLimitProperties localRateLimit() {
        return rateLimit(ProductionConfigurationValidator.LOCAL_RATE_LIMIT_SECRET_DEFAULT);
    }

    private static ContactMailNotificationProperties enabledMail() {
        return new ContactMailNotificationProperties(
                true, "owner@example.test", "from@example.test", null, null);
    }

    private static ContactMailNotificationProperties disabledMail() {
        return new ContactMailNotificationProperties(false, null, null, null, null);
    }

    private static ObjectProvider<ClientRegistrationRepository> registrations(
            ClientRegistrationRepository value) {
        return new ObjectProvider<>() {
            @Override
            public ClientRegistrationRepository getObject() {
                if (value == null) {
                    throw new IllegalStateException("no client registration repository");
                }
                return value;
            }

            @Override
            public ClientRegistrationRepository getObject(Object... args) {
                return getObject();
            }

            @Override
            public ClientRegistrationRepository getIfAvailable() {
                return value;
            }

            @Override
            public ClientRegistrationRepository getIfUnique() {
                return value;
            }
        };
    }
}
