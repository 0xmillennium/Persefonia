package dev.persefonia.automation.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ProcessHarnessBoundaryTest {
    private static final Pattern CONSTRUCTION = Pattern.compile("\\bnew\\s+ProcessBuilder\\s*\\(");

    @Test
    void processConstructionIsCentralizedInCommandRunner() throws Exception {
        Path sources = Path.of("src/test/java");
        try (var files = Files.walk(sources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (!file.getFileName().toString().equals("CommandRunner.java")) {
                    assertThat(CONSTRUCTION.matcher(Files.readString(file)).find()).as(file.toString()).isFalse();
                }
            }
        }
    }
}
