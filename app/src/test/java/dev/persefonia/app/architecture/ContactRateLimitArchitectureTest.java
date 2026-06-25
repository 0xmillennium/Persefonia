package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ContactRateLimitArchitectureTest {
    @Test
    void platformOperationsRateLimitPortDoesNotImportRedis() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.platformoperations.application.port..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.data.redis..",
                        "io.lettuce.core..",
                        "redis.clients.jedis..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void communicationDoesNotDependOnRedisOrPlatformOperationsAdapters() {
        noClasses()
                .that().resideInAnyPackage("dev.persefonia.communication.domain..", "dev.persefonia.communication.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.data.redis..",
                        "io.lettuce.core..",
                        "redis.clients.jedis..",
                        "dev.persefonia.platformoperations.infrastructure..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void webModulesDoNotUseRedisJdbcMailOrRepositoriesDirectly() throws Exception {
        String webSources = joinedSources(List.of(
                Path.of("../web-public/src/main/java"),
                Path.of("../web-admin/src/main/java")));

        assertThat(webSources)
                .doesNotContain("StringRedisTemplate")
                .doesNotContain("RedisTemplate")
                .doesNotContain("JavaMailSender")
                .doesNotContain("JdbcTemplate")
                .doesNotContain("NamedParameterJdbcTemplate")
                .doesNotContain("Repository");
    }

    private static String joinedSources(List<Path> roots) throws IOException {
        StringBuilder joined = new StringBuilder();
        for (Path root : roots) {
            try (Stream<Path> paths = Files.walk(root)) {
                joined.append(paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .map(ContactRateLimitArchitectureTest::read)
                        .reduce("", String::concat));
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
