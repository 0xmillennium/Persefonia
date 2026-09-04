package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.platformoperations.recovery.RecoveryVerificationCoordinator;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class RecoveryReadinessArchitectureTest {
    @Test
    void mediaRecoveryApplicationSemanticsRemainMediaOwnedAndFrameworkFree() {
        noClasses().that().resideInAPackage("dev.persefonia.medialibrary.application.recovery..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.platformoperations..", "dev.persefonia.webadmin..",
                        "dev.persefonia.app..", "org.springframework..", "java.sql..", "java.nio.file..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void platformRecoveryContractsDoNotDependOnMediaLibrary() {
        noClasses().that().resideInAPackage("dev.persefonia.platformoperations.application.recovery..")
                .should().dependOnClassesThat().resideInAPackage("dev.persefonia.medialibrary..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void coordinatorIsNontransactionalAndIndependentOfAuxiliaryOrMutationPorts() {
        assertThat(RecoveryVerificationCoordinator.class.getAnnotation(Transactional.class)).isNull();
        assertThat(RecoveryVerificationCoordinator.class.getDeclaredMethods())
                .allSatisfy(method -> assertThat(method.getAnnotation(Transactional.class)).isNull());
        assertThat(RecoveryVerificationCoordinator.class.getDeclaredFields())
                .extracting(field -> field.getType().getName())
                .noneMatch(name -> name.contains("Redis") || name.contains("CachePurge")
                        || name.contains("Session") || name.contains("Audit") || name.contains("Command"));
    }

    @Test
    void stepNineAddsNoMigrationOrCrossContextAssetForeignKey() throws Exception {
        Path migrations = Path.of("src/main/resources/db/migration");
        try (var files = Files.list(migrations)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith("V22__"));
        }
        for (String migration : java.util.List.of(
                "V3__publishing.sql", "V4__discovery.sql", "V11__portfolio_core.sql",
                "V16__active_cv_profile.sql")) {
            assertThat(Files.readString(migrations.resolve(migration)))
                    .doesNotContain("REFERENCES media.assets");
        }
    }
}
