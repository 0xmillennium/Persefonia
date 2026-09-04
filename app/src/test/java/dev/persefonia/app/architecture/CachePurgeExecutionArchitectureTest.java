package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.platformoperations.cache.execution.CachePurgeExecutionCoordinator;
import dev.persefonia.app.platformoperations.cache.execution.CachePurgeMetrics;
import dev.persefonia.app.platformoperations.cache.execution.CachePurgeTransactionService;
import dev.persefonia.app.platformoperations.cache.execution.NonTransactionalCachePurgeInvoker;
import dev.persefonia.app.platformoperations.cache.provider.CloudflareCachePurgeAdapter;
import dev.persefonia.app.platformoperations.cache.provider.LocalCachePurgeAdapter;
import dev.persefonia.app.platformoperations.operations.CacheInvalidationRecoveryCoordinator;
import dev.persefonia.app.platformoperations.operations.RecoveryRequestTransactionService;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationExecutionPort;
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
                .filteredOn(method -> Set.of(
                                "createAndReserve", "reserveInitial", "reserveManualRetry",
                                "reserveStrandedReplay", "recordResult")
                        .contains(method.getName()))
                .hasSize(5)
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
    void coordinatorHasOnlyPhaseServicesClockAndMetricsAsOperationalDependencies() {
        Set<Class<?>> fields = java.util.Arrays.stream(CachePurgeExecutionCoordinator.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getType())
                .collect(Collectors.toSet());
        assertThat(fields).containsExactlyInAnyOrder(
                CachePurgeTransactionService.class, NonTransactionalCachePurgeInvoker.class,
                Clock.class, CachePurgeMetrics.class);
    }

    @Test
    void recoveryCoordinatorCommitsRequiredPreflightBeforeCallingExecutionWithoutAnEncompassingTransaction()
            throws NoSuchMethodException {
        assertThat(CacheInvalidationRecoveryCoordinator.class.getAnnotation(Transactional.class)).isNull();
        assertThat(CacheInvalidationRecoveryCoordinator.class.getDeclaredMethods())
                .allMatch(method -> method.getAnnotation(Transactional.class) == null);
        assertThat(RecoveryRequestTransactionService.class.getDeclaredMethod(
                        "preflight",
                        dev.persefonia.platformoperations.application.operations.CacheInvalidationRecoveryCommand.class,
                        dev.persefonia.platformoperations.application.operations.CacheRecoveryAction.class)
                .getAnnotation(Transactional.class))
                .isNotNull()
                .extracting(Transactional::propagation)
                .isEqualTo(Propagation.REQUIRED);

        Set<Class<?>> fields = java.util.Arrays.stream(CacheInvalidationRecoveryCoordinator.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getType())
                .collect(Collectors.toSet());
        assertThat(fields).containsExactlyInAnyOrder(
                RecoveryRequestTransactionService.class, CacheInvalidationExecutionPort.class);
    }

    @Test
    void executionAndProvidersDoNotDependOnApplicationCachesSessionsOrRetryInfrastructure() {
        noClasses().that().resideInAnyPackage(
                        EXECUTION, PROVIDER, "dev.persefonia.app.platformoperations.operations..")
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
