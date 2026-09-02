package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.communication.application.command.ContactMessageStatusCommandGateway;
import dev.persefonia.discovery.application.service.AdminRedirectCommandGateway;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class AdminMutationTransactionBoundaryArchitectureTest {
    @Test
    void transactionalAdaptersResideInAppAndImplementSourceOwnedGateways() {
        classes()
                .that().haveSimpleName("TransactionalContactMessageStatusCommandGateway")
                .should().resideInAPackage("dev.persefonia.app.communication.application")
                .andShould().beAssignableTo(ContactMessageStatusCommandGateway.class)
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);

        classes()
                .that().haveSimpleName("TransactionalAdminRedirectCommandGateway")
                .should().resideInAPackage("dev.persefonia.app.discovery.application")
                .andShould().beAssignableTo(AdminRedirectCommandGateway.class)
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void sourceApplicationModulesDoNotDependOnSpringTransactions() {
        noClasses()
                .that().resideInAnyPackage(
                        "dev.persefonia.communication.application..",
                        "dev.persefonia.discovery.application..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework.transaction..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webAdminControllersAreNotTransactional() {
        noClasses()
                .that().haveSimpleName("AdminContactController")
                .or().haveSimpleName("AdminRedirectController")
                .should().beAnnotatedWith(Transactional.class)
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void contactAndRedirectControllersAvoidRepositoriesAndConcreteMutationServices() {
        noClasses()
                .that().haveSimpleName("AdminContactController")
                .or().haveSimpleName("AdminRedirectController")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .orShould().dependOnClassesThat().haveSimpleName("UpdateContactMessageStatusCommandService")
                .orShould().dependOnClassesThat().haveSimpleName("AdminRedirectCommandService")
                .orShould().dependOnClassesThat().haveSimpleName("RedirectRuleCommandService")
                .orShould().dependOnClassesThat().haveSimpleName("RedirectRuleLifecycleService")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void transactionalGatewaysDoNotUseRequiresNewAndRedirectAdapterHasNoBroadCatch() throws IOException {
        String contactGateway = Files.readString(Path.of(
                "src/main/java/dev/persefonia/app/communication/application/TransactionalContactMessageStatusCommandGateway.java"));
        String redirectGateway = Files.readString(Path.of(
                "src/main/java/dev/persefonia/app/discovery/application/TransactionalAdminRedirectCommandGateway.java"));
        String redirectWebAdapter = Files.readString(Path.of(
                "src/main/java/dev/persefonia/app/webadmin/discovery/DiscoveryAdminRedirectGateway.java"));

        assertThat(contactGateway).doesNotContain("REQUIRES_NEW");
        assertThat(redirectGateway).doesNotContain("REQUIRES_NEW");
        assertThat(redirectWebAdapter).doesNotContain("catch (RuntimeException");
    }
}
