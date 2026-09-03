package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.platformoperations.cache.execution.CachePurgeExecutionCoordinator;
import dev.persefonia.app.platformoperations.cache.execution.CachePurgeTransactionService;
import dev.persefonia.app.platformoperations.cache.execution.NonTransactionalCachePurgeInvoker;
import dev.persefonia.app.platformoperations.cache.provider.CloudflareCachePurgeAdapter;
import dev.persefonia.app.platformoperations.cache.provider.LocalCachePurgeAdapter;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class CachePurgeExecutionArchitectureTest {
    private static final String EXECUTION = "dev.persefonia.app.platformoperations.cache.execution..";
    private static final String PROVIDER = "dev.persefonia.app.platformoperations.cache.provider..";

    @Test
    void transactionPhasesUseSeparateProxiedBeansWithExactPropagation() throws NoSuchMethodException {
        assertThat(CachePurgeExecutionCoordinator.class.getAnnotation(Transactional.class)).isNull();
        assertThat(CachePurgeExecutionCoordinator.class.getDeclaredMethods())
                .allMatch(method -> method.getAnnotation(Transactional.class) == null);

        assertThat(CachePurgeTransactionService.class.getDeclaredMethods())
                .filteredOn(method -> Set.of("createAndReserve", "reserveInitial", "reserveManualRetry", "recordResult")
                        .contains(method.getName()))
                .hasSize(4)
                .allSatisfy(method -> assertThat(method.getAnnotation(Transactional.class))
                        .isNotNull()
                        .extracting(Transactional::propagation)
                        .isEqualTo(Propagation.REQUIRES_NEW));

        assertThat(NonTransactionalCachePurgeInvoker.class.getDeclaredMethod(
                "invoke", dev.persefonia.app.platformoperations.cache.execution.CachePurgeWorkItem.class)
                .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
        assertThat(LocalCachePurgeAdapter.class.getAnnotation(Transactional.class)).isNull();
        assertThat(CloudflareCachePurgeAdapter.class.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void coordinatorHasOnlyPhaseServicesAndClockAsOperationalDependencies() {
        Set<Class<?>> fields = java.util.Arrays.stream(CachePurgeExecutionCoordinator.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getType())
                .collect(Collectors.toSet());
        assertThat(fields).containsExactlyInAnyOrder(
                CachePurgeTransactionService.class, NonTransactionalCachePurgeInvoker.class, Clock.class);
    }

    @Test
    void executionAndProvidersDoNotDependOnApplicationCachesSessionsOrRetryInfrastructure() {
        noClasses().that().resideInAnyPackage(EXECUTION, PROVIDER)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.cache..", "org.springframework.data.redis..", "jakarta.servlet.http..",
                        "javax.servlet.http..", "org.springframework.retry..", "io.github.resilience4j.retry..",
                        "org.springframework.scheduling..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void providerAdaptersHaveNoPersistenceOrSourceDependencies() {
        noClasses().that().resideInAPackage(PROVIDER)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.jdbc..", "javax.sql..", "java.sql..",
                        "dev.persefonia.contentpublishing..", "dev.persefonia.profileportfolio..",
                        "dev.persefonia.discovery..", "dev.persefonia.medialibrary..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void sourceTransactionalGatewaysDoNotDependOnPurgeExecutionYet() {
        noClasses().that().haveSimpleNameStartingWith("Transactional")
                .and().haveSimpleNameEndingWith("Gateway")
                .should().dependOnClassesThat().resideInAnyPackage(
                        EXECUTION, "dev.persefonia.platformoperations.application.cache..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}
