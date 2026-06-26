package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ContactMailNotificationArchitectureTest {
    @Test
    void communicationDoesNotDependOnSpringMailOrMailApis() {
        noClasses()
                .that().resideInAnyPackage("dev.persefonia.communication.domain..", "dev.persefonia.communication.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.mail..",
                        "jakarta.mail..",
                        "javax.mail..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webModulesDoNotUseJavaMailSenderDirectly() throws Exception {
        String webSources = joinedSources(List.of(
                Path.of("../web-public/src/main/java"),
                Path.of("../web-admin/src/main/java")));

        assertThat(webSources).doesNotContain("JavaMailSender");
    }

    @Test
    void webModulesDoNotUseMailNotificationOrPostCommitShortcuts() throws Exception {
        String webSources = joinedSources(List.of(
                Path.of("../web-public/src/main/java"),
                Path.of("../web-admin/src/main/java")));

        assertThat(webSources)
                .doesNotContain("MailNotificationPort")
                .doesNotContain("PostCommitTaskExecutor")
                .doesNotContain("ContactMessageRepository");
    }

    @Test
    void springMailAdapterExistsOnlyInAppCommunicationMail() throws Exception {
        String outsideAppMailSources = joinedSources(List.of(
                Path.of("../communication/src/main/java"),
                Path.of("../web-public/src/main/java"),
                Path.of("../web-admin/src/main/java")));
        String appMailSources = joinedSources(List.of(Path.of("src/main/java/dev/persefonia/app/communication/mail")));

        assertThat(outsideAppMailSources)
                .doesNotContain("JavaMailSender")
                .doesNotContain("SimpleMailMessage")
                .doesNotContain("MimeMessageHelper");
        assertThat(appMailSources)
                .contains("JavaMailSender")
                .contains("SimpleMailMessage");
    }

    private static String joinedSources(List<Path> roots) throws IOException {
        StringBuilder joined = new StringBuilder();
        for (Path root : roots) {
            try (Stream<Path> paths = Files.walk(root)) {
                joined.append(paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .map(ContactMailNotificationArchitectureTest::read)
                        .reduce("", (left, right) -> left.concat(right)));
            }
        }
        return joined.toString();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}
