package dev.persefonia.app.discovery;

import dev.persefonia.discovery.application.service.DiscoverableResourceProjectionService;
import dev.persefonia.discovery.application.service.PublicRouteResolutionService;
import dev.persefonia.discovery.application.service.RedirectRuleCommandService;
import dev.persefonia.discovery.application.service.RedirectRuleLifecycleService;
import dev.persefonia.discovery.application.service.RedirectRuleQueryService;
import dev.persefonia.discovery.domain.DiscoverableResourceRepository;
import dev.persefonia.discovery.domain.RedirectRuleRepository;
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
}
