package dev.persefonia.app.discovery;

import dev.persefonia.app.discovery.application.IdentityAccessAdminRedirectCommandAuthorizationPolicy;
import dev.persefonia.discovery.application.authorization.AdminRedirectCommandAuthorizationPolicy;
import dev.persefonia.discovery.application.port.CreateRedirectRulePort;
import dev.persefonia.discovery.application.port.DeactivateRedirectRulePort;
import dev.persefonia.discovery.application.service.AdminRedirectCommandService;
import dev.persefonia.discovery.application.service.DiscoverableResourceProjectionService;
import dev.persefonia.discovery.application.service.PublicRouteResolutionService;
import dev.persefonia.discovery.application.service.RedirectRuleCommandService;
import dev.persefonia.discovery.application.service.RedirectRuleLifecycleService;
import dev.persefonia.discovery.application.service.RedirectRuleQueryService;
import dev.persefonia.discovery.domain.DiscoverableResourceRepository;
import dev.persefonia.discovery.domain.RedirectRuleRepository;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class DiscoveryApplicationConfiguration {
    @Bean
    DiscoverableResourceProjectionService discoverableResourceProjectionService(
            DiscoverableResourceRepository repository) {
        return new DiscoverableResourceProjectionService(repository, Clock.systemUTC());
    }

    @Bean
    PublicRouteResolutionService publicRouteResolutionService(
            RedirectRuleRepository redirectRuleRepository,
            DiscoverableResourceRepository discoverableResourceRepository) {
        return new PublicRouteResolutionService(redirectRuleRepository, discoverableResourceRepository);
    }

    @Bean
    RedirectRuleCommandService redirectRuleCommandService(RedirectRuleRepository repository) {
        return new RedirectRuleCommandService(repository, Clock.systemUTC());
    }

    @Bean
    RedirectRuleQueryService redirectRuleQueryService(RedirectRuleRepository repository) {
        return new RedirectRuleQueryService(repository);
    }

    @Bean
    RedirectRuleLifecycleService redirectRuleLifecycleService(RedirectRuleRepository repository) {
        return new RedirectRuleLifecycleService(repository, Clock.systemUTC());
    }

    @Bean
    AdminRedirectCommandAuthorizationPolicy adminRedirectCommandAuthorizationPolicy(
            AdminCommandAuthorizationPolicy policy) {
        return new IdentityAccessAdminRedirectCommandAuthorizationPolicy(policy);
    }

    @Bean
    AdminRedirectCommandService adminRedirectCommandService(
            CreateRedirectRulePort creates,
            DeactivateRedirectRulePort deactivates,
            AdminRedirectCommandAuthorizationPolicy authorization) {
        return new AdminRedirectCommandService(creates, deactivates, authorization);
    }
}
