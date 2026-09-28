package dev.persefonia.automation.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.CommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutomationRepositoryPolicyTest {
    @TempDir Path temporary;

    @Test
    void productionShellSourcesHaveApprovedOwnershipAndExecutionContract() throws Exception {
        Path repository = Path.of("..").toAbsolutePath().normalize();
        CommandRunner runner = new CommandRunner(repository, temporary, Duration.ofSeconds(10), Map.of());
        var result = runner.run("git", "ls-files", "--stage", "-z", "--", "*.sh");
        result.requireSuccess();
        int productionCount = 0;
        for (String record : result.stdout().split("\0")) {
            if (record.isEmpty()) continue;
            String[] parts = record.split("\\t", 2);
            assertThat(parts).hasSize(2);
            String mode = parts[0].substring(0, 6);
            String file = parts[1];
            Path path = repository.resolve(file);
            assertThat(Files.isSymbolicLink(path)).as(file).isFalse();
            if (file.startsWith("automation-tests/src/test/")) continue;
            assertThat(file).matches("scripts/(ci|release|deploy)/[^/]+\\.sh");
            assertThat(mode).as(file).isEqualTo("100755");
            String source = Files.readString(path);
            assertThat(source).as(file).startsWith("#!/usr/bin/env bash\n");
            assertThat(source).as(file).contains("set -euo pipefail");
            assertThat(source).as(file).doesNotContain("TEST_MODE", "SKIP_SECURITY_CHECK_FOR_TESTS");
            productionCount++;
        }
        assertThat(productionCount).isGreaterThanOrEqualTo(16);
    }
}
