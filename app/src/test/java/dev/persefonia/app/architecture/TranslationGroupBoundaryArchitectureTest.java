package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.translation.port.TranslationGroupRepository;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TranslationGroupBoundaryArchitectureTest {
    @Test
    void contentItemDoesNotOwnTranslationGroupForeignKey() {
        boolean ownsTranslationState = Arrays.stream(ContentItem.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .anyMatch(name -> name.contains("translation") || name.contains("Translation"));

        assertThat(ownsTranslationState)
                .as("ContentItem must not own translation relationship state")
                .isFalse();
    }

    @Test
    void webAdminDoesNotAccessTranslationRepositories() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin..")
                .should().dependOnClassesThat().haveFullyQualifiedName(TranslationGroupRepository.class.getName())
                .orShould().dependOnClassesThat().resideInAPackage(
                        "dev.persefonia.contentpublishing.domain.translation.port..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webPublicDoesNotAccessTranslationRepositories() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webpublic..")
                .should().dependOnClassesThat().haveFullyQualifiedName(TranslationGroupRepository.class.getName())
                .orShould().dependOnClassesThat().resideInAPackage(
                        "dev.persefonia.contentpublishing.domain.translation.port..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webAdminDoesNotAccessTranslationPersistenceOrJdbc() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app.contentpublishing.persistence..",
                        "org.springframework.jdbc..",
                        "java.sql..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webPublicDoesNotAccessTranslationPersistenceOrJdbc() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webpublic..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app.contentpublishing.persistence..",
                        "org.springframework.jdbc..",
                        "java.sql..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void discoveryIsNotTouchedByTranslationGroups() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.discovery..")
                .should().dependOnClassesThat().resideInAPackage(
                        "dev.persefonia.contentpublishing.domain.translation..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }
}
