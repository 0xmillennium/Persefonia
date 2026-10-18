package dev.persefonia.app;

import dev.persefonia.app.webpublic.content.InMemoryPublicRouteResolver;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** An empty public route store for MVC contexts that deliberately have no database. */
@TestConfiguration(proxyBeanMethods = false)
public class TestPublicRouteResolutionConfiguration {
    @Bean
    @Primary
    InMemoryPublicRouteResolver publicContentTestRouteResolver() {
        return new InMemoryPublicRouteResolver();
    }
}
