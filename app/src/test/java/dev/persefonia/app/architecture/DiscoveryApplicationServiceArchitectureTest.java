package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.discovery.application.service.DiscoverableResourceProjectionService;
import dev.persefonia.discovery.application.service.PublicRouteResolutionService;
import dev.persefonia.discovery.application.service.RedirectRuleCommandService;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class DiscoveryApplicationServiceArchitectureTest {
    @Test
    void discoveryApplicationServicesRemainFrameworkFreeAndContextIndependent() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.discovery.application.service..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "java.sql..",
                        "javax.sql..",
                        "javax.servlet..",
                        "jakarta.servlet..",
                        "javax.persistence..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "dev.persefonia.app..",
                        "dev.persefonia.contentpublishing..",
                        "dev.persefonia.webpublic..",
                        "dev.persefonia.webadmin..")
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void servicesAndAppConfigurationRemainFocused() throws ClassNotFoundException {
        Class<?> configuration =
                Class.forName("dev.persefonia.app.discovery.DiscoveryApplicationConfiguration");

        assertThat(DiscoverableResourceProjectionService.class.getInterfaces()).hasSize(2);
        assertThat(PublicRouteResolutionService.class.getInterfaces()).hasSize(1);
        assertThat(RedirectRuleCommandService.class.getInterfaces()).hasSize(1);
        assertThat(configuration.getDeclaredMethods())
                .allMatch(method -> method.isAnnotationPresent(org.springframework.context.annotation.Bean.class))
                .allMatch(method -> !Modifier.isStatic(method.getModifiers()));
    }
}
