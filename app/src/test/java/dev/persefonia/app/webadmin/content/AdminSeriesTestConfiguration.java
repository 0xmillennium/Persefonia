package dev.persefonia.app.webadmin.content;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("admin-series-mvc-test")
class AdminSeriesTestConfiguration {
    @Bean
    @Primary
    AdminSeriesTestRepository adminSeriesTestRepository() {
        return new AdminSeriesTestRepository();
    }
}
