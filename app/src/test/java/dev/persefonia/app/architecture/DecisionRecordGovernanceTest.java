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
    private static final Pattern INTERNAL_MILESTONE = Pattern.compile("\\b(?:D[0-9]+|Sprint\\s+[0-9]+)\\b");
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

    private static final Pattern HEADING_LINE = Pattern.compile("(?m)^(#{2,6}\\s+.+)$");
    private static final Pattern STATUS_ROW =
            Pattern.compile("(?m)^\\| Status \\| (Proposed|Accepted|Superseded|Deprecated|Rejected) \\|$");
    private static final Pattern DATE_ROW =
            Pattern.compile("(?m)^\\| Date \\| \\d{4}-\\d{2}-\\d{2} \\|$");
    private static final Pattern SCOPE_ROW = Pattern.compile(
            "(?m)^\\| Scope \\| (architecture|security|persistence|operations|testing|documentation)"
                    + "(?: / (architecture|security|persistence|operations|testing|documentation)){0,2} \\|$");
    private static final Pattern SUPERSEDES_ROW =
            Pattern.compile("(?m)^\\| Supersedes \\| (?:none|ADR \\d{4}(?:, ADR \\d{4})*) \\|$");
    private static final Pattern SUPERSEDED_BY_ROW =
            Pattern.compile("(?m)^\\| Superseded by \\| (?:none|ADR \\d{4}(?:, ADR \\d{4})*) \\|$");
    private static final int MAX_ADR_LINES = 75;
    private static final int MAX_ADR_WORDS = 420;
    private static final int MAX_ADR_BYTES = 3_200;
    private static final int MAX_PARAGRAPH_WORDS = 65;

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
            List<String> headings = HEADING_LINE.matcher(content)
                    .results()
                    .map(match -> match.group(1))
                    .toList();

            assertThat(content)
                    .as(decision.toString())
                    .contains(REQUIRED_SECTIONS.toArray(String[]::new))
                    .contains(REQUIRED_METADATA_MARKERS.toArray(String[]::new));
            assertThat(headings)
                    .as(decision + " section headings")
                    .containsExactlyElementsOf(REQUIRED_SECTIONS);
            assertThat(STATUS_ROW.matcher(content).find()).as(decision + " Status row").isTrue();
            assertThat(DATE_ROW.matcher(content).find()).as(decision + " Date row").isTrue();
            assertThat(SCOPE_ROW.matcher(content).find()).as(decision + " Scope row").isTrue();
            assertThat(SUPERSEDES_ROW.matcher(content).find()).as(decision + " Supersedes row").isTrue();
            assertThat(SUPERSEDED_BY_ROW.matcher(content).find()).as(decision + " Superseded by row").isTrue();
        }
    }

    @Test
    void decisionRecordsStayWithinConcisenessAndReadabilityBudgets() throws Exception {
        for (Path decision : decisionRecords()) {
            String content = Files.readString(decision);
            List<String> lines = content.lines().toList();
            int wordCount = countWords(content);
            int byteCount = Files.readAllBytes(decision).length;

            assertThat(lines.size())
                    .as(decision + " line count")
                    .isLessThanOrEqualTo(MAX_ADR_LINES);
            assertThat(wordCount)
                    .as(decision + " word count")
                    .isLessThanOrEqualTo(MAX_ADR_WORDS);
            assertThat(byteCount)
                    .as(decision + " byte size")
                    .isLessThanOrEqualTo(MAX_ADR_BYTES);

            boolean inCodeBlock = false;
            for (String line : lines) {
                if (line.startsWith("```")) {
                    inCodeBlock = !inCodeBlock;
                    continue;
                }
                if (inCodeBlock || line.isBlank() || line.startsWith("|") || line.startsWith("#")) {
                    continue;
                }
                assertThat(countWords(line))
                        .as(decision + " paragraph/bullet word count: " + line)
                        .isLessThanOrEqualTo(MAX_PARAGRAPH_WORDS);
            }
        }
    }

    @Test
    void publicDecisionRecordsAndIndexDoNotExposeInternalMilestones() throws Exception {
        for (Path document : java.util.stream.Stream.concat(decisionRecords().stream(), java.util.stream.Stream.of(INDEX)).toList()) {
            assertThat(INTERNAL_MILESTONE.matcher(Files.readString(document)).find())
                    .as(document.toString()).isFalse();
        }
    }

    @Test
    void longLivedDocumentationRemainsLimitedToDecisionRecords() {
        Path docs = DECISION_DIR.getParent();
        for (String directory : List.of("architecture", "verification", "testing", "release",
                "checklists", "process")) {
            assertThat(docs.resolve(directory)).doesNotExist();
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

    private static int countWords(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }
}
