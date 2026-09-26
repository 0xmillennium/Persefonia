package dev.persefonia.app.identityaccess.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.persefonia.identityaccess.domain.admin.OidcGroup;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessPolicy;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminAccessProperties.class)
public class AdminAccessConfiguration {
    @Bean
    AdminAccessPolicy adminAccessPolicy(AdminAccessProperties properties) {
        return AdminAccessPolicy.of(
                OidcGroup.of(properties.getRequiredOidcGroup()),
                properties.isInitialOwnerBootstrapEnabled(),
                properties.isAutomaticProvisioningEnabled());
    }

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
