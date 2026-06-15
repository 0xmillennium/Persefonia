package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class InternalWorkflowLanguageHygieneTest {
    private static final List<Path> DURABLE_ROOTS = List.of(
            Path.of("../docs/decisions"),
            Path.of("src/main"),
            Path.of("src/test"),
            Path.of("../content-publishing/src/main"),
            Path.of("../content-publishing/src/test"),
            Path.of("../discovery/src/main"),
            Path.of("../discovery/src/test"),
            Path.of("../web-public/src/main"),
            Path.of("../web-public/src/test"),
            Path.of("../web-admin/src/main"),
            Path.of("../web-admin/src/test"),
            Path.of("../taxonomy/src/main"),
            Path.of("../taxonomy/src/test"));
    private static final Pattern INTERNAL_WORKFLOW_LANGUAGE = Pattern.compile(String.join("|",
            "\\b" + "Spr" + "int\\s+\\d+\\b",
            "\\b" + "Spr" + "int\\d+\\b",
            "\\bS\\d+-P\\d+\\b",
            "\\bS\\d+-B\\d+\\b",
            "\\b" + "Ste" + "p\\s+\\d+\\b"));

    @Test
    void durableDecisionRecordsAndCodeDoNotContainInternalWorkflowLanguage() throws Exception {
        List<String> matches = durableFiles()
                .flatMap(InternalWorkflowLanguageHygieneTest::matchesInPathOrContent)
                .toList();

        assertThat(matches).isEmpty();
    }

    private static Stream<Path> durableFiles() {
        return DURABLE_ROOTS.stream()
                .filter(Files::exists)
                .flatMap(InternalWorkflowLanguageHygieneTest::walk);
    }

    private static Stream<Path> walk(Path root) {
        try {
            return Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(InternalWorkflowLanguageHygieneTest::isDurableSource);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan " + root, exception);
        }
    }

    private static boolean isDurableSource(Path path) {
        String normalized = path.normalize().toString();
        return !normalized.contains("/build/")
                && !normalized.contains("/out/")
                && !normalized.contains("/target/")
                && !normalized.contains("/.gradle/")
                && !normalized.contains("/node_modules/")
                && (normalized.endsWith(".md") || normalized.endsWith(".java"));
    }

    private static Stream<String> matchesInPathOrContent(Path path) {
        Stream<String> pathMatches = INTERNAL_WORKFLOW_LANGUAGE.matcher(path.toString()).find()
                ? Stream.of(path + " filename")
                : Stream.empty();

        try {
            String content = Files.readString(path);
            List<String> contentMatches = content.lines()
                    .filter(line -> INTERNAL_WORKFLOW_LANGUAGE.matcher(line).find())
                    .map(line -> path + ": " + line.trim())
                    .toList();
            return Stream.concat(pathMatches, contentMatches.stream());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}
