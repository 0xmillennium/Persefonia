package dev.persefonia.app.security.cache;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class SensitiveRouteCacheHeadersConfiguration {
    @Bean
    FilterRegistrationBean<SensitiveRouteCacheHeadersFilter> sensitiveRouteCacheHeadersFilterRegistration() {
        FilterRegistrationBean<SensitiveRouteCacheHeadersFilter> registration =
                new FilterRegistrationBean<>(new SensitiveRouteCacheHeadersFilter());
        registration.setName("sensitiveRouteCacheHeadersFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
