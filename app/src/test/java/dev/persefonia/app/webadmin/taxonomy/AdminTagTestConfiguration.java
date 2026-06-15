package dev.persefonia.app.webadmin.taxonomy;

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
}
