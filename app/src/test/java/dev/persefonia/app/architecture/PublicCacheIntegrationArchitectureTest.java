package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationRegistrar;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheTargetPlanner;
import dev.persefonia.app.transaction.PostCommitTaskExecutor;
import dev.persefonia.platformoperations.domain.cache.CacheTargetType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PublicCacheIntegrationArchitectureTest {
    @Test
    void sourceContextsRemainIndependentFromPlatformOperations() {
        noClasses()
                .that().resideInAnyPackage(
                        "dev.persefonia.contentpublishing..",
                        "dev.persefonia.taxonomy..",
                        "dev.persefonia.profileportfolio..",
                        "dev.persefonia.medialibrary..",
                        "dev.persefonia.discovery..")
                .should().dependOnClassesThat().resideInAPackage("dev.persefonia.platformoperations..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void transactionalGatewaysUseRegistrarAndNotExecutionOrPersistenceInfrastructure() {
        noClasses()
                .that().haveSimpleNameStartingWith("Transactional")
                .and().haveSimpleNameEndingWith("Gateway")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app.platformoperations.cache.execution..",
                        "dev.persefonia.app.platformoperations.cache.provider..",
                        "dev.persefonia.platformoperations.application.cache..",
                        "org.springframework.jdbc..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);

        assertThat(PublicCacheInvalidationRegistrar.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == PostCommitTaskExecutor.class);
    }

    @Test
    void plannerProducesUrlTargetsOnlyAndNoCacheTagHeaderIsEmitted() throws Exception {
        var request = new PublicCacheTargetPlanner().plan(java.util.List.of("/one")).orElseThrow();
        assertThat(request.targets()).allMatch(target -> target.targetType() == CacheTargetType.URL);

        try (var paths = Files.walk(Path.of("../web-public/src/main/java"))) {
            assertThat(paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try { return Files.readString(path).contains("Cache-Tag"); }
                        catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
                    }).toList()).isEmpty();
        }
    }
}
