package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards durable repository surfaces against non-durable repository vocabulary so that
 * only durable product and technical terms survive in committed code, tests,
 * and migrations.
 *
 * <p>Rejected literals are assembled from fragments so this guard does not
 * itself contain the very tokens it rejects.
 */
class RepositoryLanguageHygieneTest {
    private static final List<String> DURABLE_FILE_EXTENSIONS = List.of(
            ".java", ".sql");

    private static final List<Path> DURABLE_ROOTS = List.of(
            Path.of("../audit/src/main/java"),
            Path.of("../audit/src/test/java"),
            Path.of("src/main/java/dev/persefonia/app/audit"),
            Path.of("src/test/java/dev/persefonia/app/audit"),
            Path.of("src/main/resources/db/migration/V19__audit_foundation.sql"),
            Path.of("src/test/java/dev/persefonia/app/architecture/RepositoryLanguageHygieneTest.java"));

    private static final List<List<String>> NON_DURABLE_VOCABULARY = List.of(
            List.of("spr" + "int"),
            List.of("st" + "ep"),
            List.of("pro" + "mpt"),
            List.of("blue" + "print"),
            List.of("read" + "iness"),
            List.of("plan" + "ning"),
            List.of("ag" + "ent"),
            List.of("re" + "pair"),
            List.of("implementation", "pro" + "mpt"),
            List.of("ag" + "ent", "out" + "put"),
            List.of("read" + "iness", "ga" + "te"),
            List.of("re" + "pair", "st" + "ep"));

    @Test
    void durableRepositorySurfacesDoNotContainNonDurableRepositoryVocabulary() {
        List<String> matches = durableFiles()
                .flatMap(RepositoryLanguageHygieneTest::matchesInPathOrContent)
                .toList();

        assertThat(matches).isEmpty();
    }

    private static Stream<Path> durableFiles() {
        return DURABLE_ROOTS.stream()
                .filter(Files::exists)
                .flatMap(RepositoryLanguageHygieneTest::walk);
    }

    private static Stream<Path> walk(Path root) {
        if (Files.isRegularFile(root)) {
            return Stream.of(root).filter(RepositoryLanguageHygieneTest::isDurableSource);
        }
        try {
            return Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(RepositoryLanguageHygieneTest::isDurableSource);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan " + root, exception);
        }
    }

    private static boolean isDurableSource(Path path) {
        String normalized = path.normalize().toString();
        if (normalized.contains("/build/")
                || normalized.contains("/out/")
                || normalized.contains("/target/")
                || normalized.contains("/.gradle/")
                || normalized.contains("/node_modules/")) {
            return false;
        }
        return DURABLE_FILE_EXTENSIONS.stream().anyMatch(normalized::endsWith);
    }

    private static Stream<String> matchesInPathOrContent(Path path) {
        try {
            String content = Files.readString(path);
            List<String> tokens = tokens(path + "\n" + content);
            return containsNonDurableVocabulary(tokens)
                    ? Stream.of(path + ": non-durable repository vocabulary")
                    : Stream.empty();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static boolean containsNonDurableVocabulary(List<String> tokens) {
        return NON_DURABLE_VOCABULARY.stream()
                .anyMatch(vocabulary -> containsSequence(tokens, vocabulary));
    }

    private static boolean containsSequence(List<String> tokens, List<String> vocabulary) {
        for (int index = 0; index <= tokens.size() - vocabulary.size(); index++) {
            if (tokens.subList(index, index + vocabulary.size()).equals(vocabulary)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> tokens(String text) {
        return Stream.of(text.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                        .split("[^A-Za-z0-9]+"))
                .map(String::toLowerCase)
                .filter(token -> !token.isBlank())
                .toList();
    }
}
