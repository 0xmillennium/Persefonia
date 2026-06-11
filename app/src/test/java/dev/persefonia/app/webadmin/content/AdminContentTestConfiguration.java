package dev.persefonia.app.webadmin.content;

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
}
