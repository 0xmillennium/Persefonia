package dev.persefonia.app.security.admin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;

@Configuration(proxyBeanMethods = false)
public class AdminCommandAuthorizationConfiguration {
    @Bean
    AdminCommandAuthorizationPolicy adminCommandAuthorizationPolicy() {
        return new AdminCommandAuthorizationPolicy();
    }
}
