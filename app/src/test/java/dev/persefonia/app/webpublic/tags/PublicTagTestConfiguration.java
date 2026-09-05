package dev.persefonia.app.webpublic.tags;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class PublicTagTestConfiguration {
    @Bean
    @Primary
    PublicTagTestRepository publicTagTestRepository() {
        return new PublicTagTestRepository();
    }
}
