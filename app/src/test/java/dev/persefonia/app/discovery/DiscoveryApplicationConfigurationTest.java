package dev.persefonia.app.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.persefonia.discovery.application.authorization.AdminRedirectCommandAuthorizationPolicy;
import dev.persefonia.discovery.application.port.CreateRedirectRulePort;
import dev.persefonia.discovery.application.port.DeactivateRedirectRulePort;
import dev.persefonia.discovery.application.port.RemoveDiscoverableResourcePort;
import dev.persefonia.discovery.application.port.ResolvePublicRoutePort;
import dev.persefonia.discovery.application.port.UpdateDiscoverableResourcePort;
import dev.persefonia.discovery.application.service.AdminRedirectCommandService;
import dev.persefonia.discovery.application.service.DiscoverableResourceProjectionService;
import dev.persefonia.discovery.application.service.PublicRouteResolutionService;
import dev.persefonia.discovery.application.service.RedirectRuleCommandService;
import dev.persefonia.discovery.application.service.RedirectRuleLifecycleService;
import dev.persefonia.discovery.domain.DiscoverableResourceRepository;
import dev.persefonia.discovery.domain.RedirectRuleRepository;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DiscoveryApplicationConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(DiscoverableResourceRepository.class, () -> mock(DiscoverableResourceRepository.class))
            .withBean(RedirectRuleRepository.class, () -> mock(RedirectRuleRepository.class))
            .withBean(AdminCommandAuthorizationPolicy.class, AdminCommandAuthorizationPolicy::new)
            .withUserConfiguration(DiscoveryApplicationConfiguration.class);

    @Test
    void wiresDiscoveryApplicationPortsToFocusedServices() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(UpdateDiscoverableResourcePort.class);
            assertThat(context).hasSingleBean(RemoveDiscoverableResourcePort.class);
            assertThat(context).hasSingleBean(CreateRedirectRulePort.class);
            assertThat(context).hasSingleBean(DeactivateRedirectRulePort.class);
            assertThat(context).hasSingleBean(ResolvePublicRoutePort.class);
            assertThat(context).hasSingleBean(AdminRedirectCommandAuthorizationPolicy.class);
            assertThat(context).hasSingleBean(AdminRedirectCommandService.class);

            assertThat(context.getBean(UpdateDiscoverableResourcePort.class))
                    .isSameAs(context.getBean(DiscoverableResourceProjectionService.class))
                    .isSameAs(context.getBean(RemoveDiscoverableResourcePort.class));
            assertThat(context.getBean(CreateRedirectRulePort.class))
                    .isSameAs(context.getBean(RedirectRuleCommandService.class));
            assertThat(context.getBean(DeactivateRedirectRulePort.class))
                    .isSameAs(context.getBean(RedirectRuleLifecycleService.class));
            assertThat(context.getBean(ResolvePublicRoutePort.class))
                    .isSameAs(context.getBean(PublicRouteResolutionService.class));
        });
    }
}
