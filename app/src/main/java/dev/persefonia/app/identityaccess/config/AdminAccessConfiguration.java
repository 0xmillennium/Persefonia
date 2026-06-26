package dev.persefonia.app.identityaccess.config;

import java.time.Clock;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.NormalizedEmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessPolicy;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminAccessProperties.class)
public class AdminAccessConfiguration {
    @Bean
    AdminAccessPolicy adminAccessPolicy(AdminAccessProperties properties) {
        Set<OidcSubject> subjects = properties.getAllowlistedSubjects().stream()
                .map(value -> value.trim())
                .filter(value -> !value.isBlank())
                .map(OidcSubject::of)
                .collect(Collectors.toUnmodifiableSet());
        Set<NormalizedEmailAddress> emails = properties.getAllowlistedEmails().stream()
                .map(value -> value.trim())
                .filter(value -> !value.isBlank())
                .map(EmailAddress::of)
                .map(NormalizedEmailAddress::from)
                .collect(Collectors.toUnmodifiableSet());

        return AdminAccessPolicy.of(
                subjects,
                emails,
                properties.isInitialOwnerBootstrapEnabled(),
                properties.isAutomaticProvisioningEnabled());
    }

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
