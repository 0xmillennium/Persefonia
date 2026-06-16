package dev.persefonia.app.webpublic.series;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("public-series-mvc-test")
public class PublicSeriesTestConfiguration {
    @Bean
    @Primary
    PublicSeriesTestRepository publicSeriesTestRepository() {
        return new PublicSeriesTestRepository();
    }
}
