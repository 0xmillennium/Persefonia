package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.communication.application.TransactionalContactMessageStatusCommandGateway;
import dev.persefonia.app.contentpublishing.application.TransactionalContentApplicationGateway;
import dev.persefonia.app.contentpublishing.application.TransactionalContentTagAssignmentGateway;
import dev.persefonia.app.contentpublishing.application.TransactionalSeriesCommandGateway;
import dev.persefonia.app.contentpublishing.application.TransactionalTranslationGroupCommandGateway;
import dev.persefonia.app.discovery.application.TransactionalAdminRedirectCommandGateway;
import dev.persefonia.app.identityaccess.bootstrap.TransactionalAdminBootstrapGateway;
import dev.persefonia.app.medialibrary.application.TransactionalMediaAdminCommandGateway;
import dev.persefonia.app.profileportfolio.application.TransactionalActiveCvCommandGateway;
import dev.persefonia.app.profileportfolio.application.TransactionalPersonalProfileCommandGateway;
import dev.persefonia.app.profileportfolio.application.TransactionalProjectCommandGateway;
import dev.persefonia.app.profileportfolio.application.TransactionalSitePresentationSettingsCommandGateway;
import dev.persefonia.app.taxonomy.application.TransactionalTagCommandGateway;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.contentpublishing.application.command.PreviewContentCommand;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class MandatoryAuditGatewayArchitectureTest {
    private static final String[] SOURCE_CONTEXTS = {
            "dev.persefonia.identityaccess..",
            "dev.persefonia.taxonomy..",
            "dev.persefonia.contentpublishing..",
            "dev.persefonia.profileportfolio..",
            "dev.persefonia.medialibrary..",
            "dev.persefonia.communication..",
            "dev.persefonia.discovery..",
            "dev.persefonia.contentintegrity..",
            "dev.persefonia.insights..",
            "dev.persefonia.portability..",
            "dev.persefonia.platformoperations.."
    };
    private static final List<Class<?>> GATEWAYS = List.of(
            TransactionalContentApplicationGateway.class,
            TransactionalContentTagAssignmentGateway.class,
            TransactionalSeriesCommandGateway.class,
            TransactionalTranslationGroupCommandGateway.class,
            TransactionalTagCommandGateway.class,
            TransactionalProjectCommandGateway.class,
            TransactionalPersonalProfileCommandGateway.class,
            TransactionalSitePresentationSettingsCommandGateway.class,
            TransactionalActiveCvCommandGateway.class,
            TransactionalMediaAdminCommandGateway.class,
            TransactionalAdminRedirectCommandGateway.class,
            TransactionalContactMessageStatusCommandGateway.class,
            TransactionalAdminBootstrapGateway.class);

    @Test
    void everyCataloguedGatewayHasMandatoryAuditCompositionDependency() {
        assertThat(GATEWAYS).allSatisfy(gateway -> {
            assertThat(gateway.getDeclaredConstructors())
                    .filteredOn(constructor -> java.util.Arrays.asList(constructor.getParameterTypes())
                            .contains(AppendAuditRecordPort.class))
                    .anySatisfy(constructor -> {
                assertThat(constructor.getParameterTypes()).contains(AppendAuditRecordPort.class);
                assertThat(constructor.getParameterTypes())
                        .anyMatch(type -> type.getPackageName()
                                .equals("dev.persefonia.app.audit.integration"));
                    });
        });
    }

    @Test
    void allGatewayMutationMethodsUseRequiredReadWriteTransactions() {
        assertThat(GATEWAYS).allSatisfy(gateway -> assertThat(gateway.getDeclaredMethods())
                .filteredOn(method -> !isPreview(method))
                .allSatisfy(method -> {
                    Transactional transaction = method.getAnnotation(Transactional.class);
                    assertThat(transaction).as(gateway.getSimpleName() + "." + method.getName()).isNotNull();
                    assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRED);
                    assertThat(transaction.readOnly()).isFalse();
                }));
    }

    @Test
    void contentPreviewRemainsReadOnlyAndGatewayStillOwnsNoAuditReadAction() throws Exception {
        Method preview = TransactionalContentApplicationGateway.class
                .getDeclaredMethod("previewContent", PreviewContentCommand.class);

        assertThat(preview.getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(preview.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRED);
    }

    @Test
    void sourceModulesRemainIndependentFromAuditAndControllersCannotAppend() {
        noClasses()
                .that().resideInAnyPackage(SOURCE_CONTEXTS)
                .should().dependOnClassesThat().resideInAPackage("dev.persefonia.audit..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);

        noClasses()
                .that().resideInAPackage("dev.persefonia.webadmin..")
                .and().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().areAssignableTo(AppendAuditRecordPort.class)
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void mandatoryAuditGatewaysDoNotUsePostCommitOrTransactionSynchronization() {
        noClasses()
                .that().haveSimpleNameStartingWith("Transactional")
                .and().haveSimpleNameEndingWith("Gateway")
                .should().dependOnClassesThat().haveSimpleName("PostCommitTaskExecutor")
                .orShould().dependOnClassesThat().resideInAPackage("org.springframework.transaction.support..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    private static boolean isPreview(Method method) {
        return method.getName().equals("previewContent");
    }
}
