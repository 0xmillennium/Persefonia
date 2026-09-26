package dev.persefonia.app.security.oidc;

import java.time.Clock;
import java.util.Map;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

import dev.persefonia.app.identityaccess.session.TransactionalAdminSessionRevalidationGateway;

@Configuration(proxyBeanMethods = false)
@AutoConfiguration(after = OAuth2ClientAutoConfiguration.class)
@EnableConfigurationProperties(AdminOidcSessionProperties.class)
public class AdminOidcSessionConfiguration {
    @Bean
    @ConditionalOnBean(ClientRegistrationRepository.class)
    OAuth2AuthorizedClientRepository adminOAuth2AuthorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    @Bean
    @ConditionalOnBean(ClientRegistrationRepository.class)
    OAuth2AuthorizedClientManager adminOAuth2AuthorizedClientManager(ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientRepository clients) {
        DefaultOAuth2AuthorizedClientManager manager = new DefaultOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().refreshToken().build());
        // Admin requests cannot change the refresh scope through a query parameter.
        manager.setContextAttributesMapper(request -> Map.of());
        return manager;
    }

    @Bean
    @ConditionalOnBean(ClientRegistrationRepository.class)
    AdminOidcFreshUserService adminOidcFreshUserService(AdminOidcSessionProperties properties) {
        return new AdminOidcFreshUserService(properties);
    }

    @Bean
    @Lazy
    @ConditionalOnBean(ClientRegistrationRepository.class)
    AdminOidcSessionRevalidationService adminOidcSessionRevalidationService(OAuth2AuthorizedClientManager manager,
            AdminOidcFreshUserService freshUsers, OidcClaimMapper mapper,
            @Lazy TransactionalAdminSessionRevalidationGateway gateway) {
        return new AdminOidcSessionRevalidationService(manager, freshUsers, mapper, gateway);
    }

    @Bean
    @ConditionalOnBean(ClientRegistrationRepository.class)
    AdminOidcAuthenticationSuccessHandler adminOidcAuthenticationSuccessHandler(Clock clock) {
        return new AdminOidcAuthenticationSuccessHandler(clock);
    }
}
