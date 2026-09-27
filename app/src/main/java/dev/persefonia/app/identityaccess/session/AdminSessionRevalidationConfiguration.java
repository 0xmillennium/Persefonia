package dev.persefonia.app.identityaccess.session;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import dev.persefonia.identityaccess.application.admin.session.AdminSessionRevalidationUseCase;
import dev.persefonia.identityaccess.domain.admin.AdminAccountRepository;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessPolicy;

@Configuration(proxyBeanMethods = false)
class AdminSessionRevalidationConfiguration {
    @Bean
    @Lazy
    AdminSessionRevalidationUseCase adminSessionRevalidationUseCase(
            AdminAccountRepository repository, AdminAccessPolicy policy) {
        return new AdminSessionRevalidationUseCase(repository, policy);
    }
}
