package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DecisionRecordGovernanceTest {
    private static final Path DECISION_DIR = Path.of("../docs/decisions");
    private static final Path INDEX = DECISION_DIR.resolve("INDEX.md");
    private static final Pattern ADR_FILE_NAME = Pattern.compile("\\d{4}-[a-z0-9]+(?:-[a-z0-9]+)*\\.md");
    private static final Pattern INDEX_LINK = Pattern.compile("\\((\\d{4}-[a-z0-9]+(?:-[a-z0-9]+)*\\.md)\\)");
    private static final List<String> REQUIRED_SECTIONS = List.of(
            "## Context",
            "## Decision",
            "## Consequences",
            "## Alternatives considered",
            "## Review triggers");
    private static final List<String> REQUIRED_METADATA_MARKERS = List.of(
            "| Field | Value |",
            "| Status |",
            "| Date |",
            "| Scope |",
            "| Supersedes |",
            "| Superseded by |");

    @Test
    void decisionRecordFileNamesUseSequentialNumbersWithoutGaps() throws Exception {
        List<Path> decisions = decisionRecords();

        assertThat(decisions)
                .extracting(path -> path.getFileName().toString())
                .allMatch(name -> ADR_FILE_NAME.matcher(name).matches());

        List<Integer> numbers = decisions.stream()
                .map(path -> path.getFileName().toString())
                .map(name -> Integer.parseInt(name.substring(0, 4)))
                .toList();

        assertThat(numbers).containsExactlyElementsOf(sequentialNumbers(numbers.size()));
    }

    @Test
    void decisionIndexReferencesEveryDecisionRecordAndNoMissingRecords() throws Exception {
        Set<String> decisionFiles = decisionRecords().stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> indexLinks = INDEX_LINK.matcher(Files.readString(INDEX))
                .results()
                .map(match -> match.group(1))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(indexLinks).isEqualTo(decisionFiles);
    }

    @Test
    void decisionRecordsFollowRequiredTemplateSectionsAndMetadata() throws Exception {
        for (Path decision : decisionRecords()) {
            String content = Files.readString(decision);

            assertThat(content)
                    .as(decision.toString())
                    .contains(REQUIRED_SECTIONS.toArray(String[]::new))
                    .contains(REQUIRED_METADATA_MARKERS.toArray(String[]::new));
        }
    }

    private static List<Path> decisionRecords() throws IOException {
        try (var paths = Files.list(DECISION_DIR)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .filter(path -> !path.getFileName().toString().equals("INDEX.md"))
                    .filter(path -> !path.getFileName().toString().equals("TEMPLATE.md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static List<Integer> sequentialNumbers(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .boxed()
                .toList();
    }
}
