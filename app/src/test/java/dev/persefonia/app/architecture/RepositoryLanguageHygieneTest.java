package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards durable repository surfaces against internal planning language so that
 * only durable product and technical terms survive in committed code, tests,
 * migrations, templates, and decision records.
 *
 * <p>Forbidden literals are assembled from fragments so this guard does not
 * itself contain the very tokens it rejects (it scans its own source tree).
 */
class RepositoryLanguageHygieneTest {
    private static final List<String> MODULE_NAMES = List.of(
            "shared-kernel",
            "identity-access",
            "taxonomy",
            "content-publishing",
            "profile-portfolio",
            "media-library",
            "communication",
            "discovery",
            "content-integrity",
            "insights",
            "audit",
            "portability",
            "platform-operations",
            "web-public",
            "web-admin");

    private static final List<String> DURABLE_FILE_EXTENSIONS = List.of(
            ".java", ".md", ".sql", ".jte", ".kts");

    // Case-insensitive. Each alternative is built from fragments so the literal
    // forbidden token never appears in this file.
    private static final Pattern INTERNAL_PLANNING_LANGUAGE = Pattern.compile(
            String.join("|",
                    "\\b" + "spr" + "int\\b",
                    "\\b" + "blue" + "print\\b",
                    "implementation\\s+" + "pro" + "mpt",
                    "carry" + "-over",
                    "agent\\s+" + "out" + "put",
                    "final\\s+" + "ga" + "te",
                    "roadmap\\s+" + "st" + "ep",
                    "\\b" + "st" + "ep\\s+\\d+\\b",
                    "\\bS\\d+-P\\d+\\b",
                    "\\bS\\d+-B\\d+\\b",
                    "\\bS\\d{2,}[A-Za-z]",
                    "\\bT0\\d\\b"),
            Pattern.CASE_INSENSITIVE);

    @Test
    void durableRepositorySurfacesDoNotContainInternalPlanningLanguage() {
        List<String> matches = durableFiles()
                .flatMap(RepositoryLanguageHygieneTest::matchesInPathOrContent)
                .toList();

        assertThat(matches).isEmpty();
    }

    private static Stream<Path> durableFiles() {
        return durableRoots()
                .filter(Files::exists)
                .flatMap(RepositoryLanguageHygieneTest::walk);
    }

    private static Stream<Path> durableRoots() {
        Stream<Path> appRoots = Stream.of(
                Path.of("src/main"),
                Path.of("src/test"),
                Path.of("../docs/decisions"));
        Stream<Path> moduleRoots = MODULE_NAMES.stream()
                .map(module -> Path.of("..", module, "src"));
        return Stream.concat(appRoots, moduleRoots);
    }

    private static Stream<Path> walk(Path root) {
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
        Stream<String> pathMatches = INTERNAL_PLANNING_LANGUAGE.matcher(path.toString()).find()
                ? Stream.of(path + " filename")
                : Stream.empty();

        try {
            String content = Files.readString(path);
            List<String> contentMatches = content.lines()
                    .filter(line -> INTERNAL_PLANNING_LANGUAGE.matcher(line).find())
                    .map(line -> path + ": " + line.trim())
                    .toList();
            return Stream.concat(pathMatches, contentMatches.stream());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}
