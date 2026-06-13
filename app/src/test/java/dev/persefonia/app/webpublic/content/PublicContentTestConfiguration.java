package dev.persefonia.app.webpublic.content;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("public-content-mvc-test")
public class PublicContentTestConfiguration {
    @Bean
    @Primary
    PublicContentTestRepository publicContentTestRepository() {
        return new PublicContentTestRepository();
    }

    @Bean
    @Primary
    PublicContentTestRevisionRepository publicContentTestRevisionRepository() {
        return new PublicContentTestRevisionRepository();
    }
}
