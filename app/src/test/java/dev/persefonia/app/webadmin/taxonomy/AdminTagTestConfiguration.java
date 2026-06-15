package dev.persefonia.app.webadmin.taxonomy;

import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("admin-tag-mvc-test")
class AdminTagTestConfiguration {
    @Bean
    @Primary
    AdminTagTestRepository adminTagTestRepository() {
        return new AdminTagTestRepository();
    }

    @Bean
    @Primary
    UpdateDiscoverableResourcePort adminTagTestDiscoveryUpdatePort() {
        return input -> new DiscoverableResourceProjectionResult.Updated();
    }
}
