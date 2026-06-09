package dev.persefonia.app.observability;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RequestIdProperties.class)
public class ObservabilityConfiguration {
    @Bean
    RequestIdFilter requestIdFilter(RequestIdProperties properties) {
        return new RequestIdFilter(properties);
    }
}
