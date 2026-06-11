package dev.persefonia.app.identityaccess.bootstrap;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import dev.persefonia.identityaccess.application.admin.bootstrap.AdminBootstrapLock;
import dev.persefonia.identityaccess.application.admin.bootstrap.AdminBootstrapUseCase;
import dev.persefonia.identityaccess.domain.admin.AdminAccountRepository;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessPolicy;

@Configuration(proxyBeanMethods = false)
class AdminBootstrapConfiguration {

    @Bean
    @Lazy
    AdminBootstrapUseCase adminBootstrapUseCase(
            AdminAccountRepository repository,
            AdminAccessPolicy accessPolicy,
            AdminBootstrapLock bootstrapLock,
            Clock clock) {
        return new AdminBootstrapUseCase(repository, accessPolicy, bootstrapLock, clock);
    }
}
