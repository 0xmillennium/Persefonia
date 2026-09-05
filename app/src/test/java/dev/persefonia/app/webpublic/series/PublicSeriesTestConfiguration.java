package dev.persefonia.app.webpublic.series;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class PublicSeriesTestConfiguration {
    @Bean
    @Primary
    PublicSeriesTestRepository publicSeriesTestRepository() {
        return new PublicSeriesTestRepository();
    }
}
