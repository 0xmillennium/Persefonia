package dev.persefonia.app.webadmin.content;

import dev.persefonia.discovery.application.port.CreateRedirectRulePort;
import dev.persefonia.discovery.application.port.RemoveDiscoverableResourcePort;
import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("admin-content-mvc-test")
class AdminContentTestConfiguration {
    @Bean
    @Primary
    AdminContentTestRepository adminContentTestRepository() {
        return new AdminContentTestRepository();
    }

    @Bean
    @Primary
    AdminContentTestRevisionRepository adminContentTestRevisionRepository() {
        return new AdminContentTestRevisionRepository();
    }

    @Bean
    @Primary
    UpdateDiscoverableResourcePort adminContentTestUpdateDiscoverableResourcePort() {
        return input -> new DiscoverableResourceProjectionResult.Updated();
    }

    @Bean
    @Primary
    RemoveDiscoverableResourcePort adminContentTestRemoveDiscoverableResourcePort() {
        return command -> new DiscoverableResourceProjectionResult.Removed();
    }

    @Bean
    @Primary
    CreateRedirectRulePort adminContentTestCreateRedirectRulePort() {
        return command -> new RedirectRuleCreationResult.Created();
    }
}
