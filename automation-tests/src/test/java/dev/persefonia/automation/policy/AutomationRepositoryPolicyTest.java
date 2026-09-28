package dev.persefonia.automation.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.automation.support.CommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutomationRepositoryPolicyTest {
    private static final List<Path> PRODUCTION_ROOTS = List.of(
            Path.of("scripts/ci"), Path.of("scripts/release"), Path.of("scripts/deploy"));
    @TempDir Path temporary;

    @Test
    void productionShellSourcesHaveApprovedOwnershipAndExecutionContract() throws Exception {
        Path repository = Path.of("..").toAbsolutePath().normalize();
        CommandRunner runner = new CommandRunner(repository, temporary, Duration.ofSeconds(10), Map.of());
        var result = runner.run("git", "ls-files", "--stage", "-z", "--", "*.sh");
        result.requireSuccess();
        for (String record : result.stdout().split("\0")) {
            if (record.isEmpty()) continue;
            String[] parts = record.split("\\t", 2);
            assertThat(parts).hasSize(2);
            String mode = parts[0].substring(0, 6);
            String file = parts[1];
            if (file.startsWith("automation-tests/src/test/")) continue;
            assertProductionShell(repository, file, mode);
        }
    }

    @Test
    void recursiveProductionRootsAcceptNestedScriptsAndRejectInvalidSources() throws Exception {
        String valid = "#!/usr/bin/env bash\nset -euo pipefail\n";
        for (String file : List.of("scripts/release/lib/example.sh", "scripts/ci/helpers/example.sh")) {
            writeFixture(file, valid);
            assertProductionShell(temporary, file, "100755");
        }
        for (String file : List.of("unrelated/example.sh", "scripts/release/../outside/example.sh")) {
            writeFixture(file, valid);
            assertThatThrownBy(() -> assertProductionShell(temporary, file, "100755"))
                    .as(file).isInstanceOf(AssertionError.class);
        }
        String owned = "scripts/deploy/example.sh";
        writeFixture(owned, valid);
        assertThatThrownBy(() -> assertProductionShell(temporary, owned, "100644"))
                .isInstanceOf(AssertionError.class);
        for (String invalid : List.of("#!/bin/bash\nset -euo pipefail\n", "#!/usr/bin/env bash\necho unsafe\n",
                "#!/usr/bin/env bash\n# set -euo pipefail\necho unsafe\n")) {
            writeFixture(owned, invalid);
            assertThatThrownBy(() -> assertProductionShell(temporary, owned, "100755"))
                    .isInstanceOf(AssertionError.class);
        }
        Path symlink = temporary.resolve("scripts/ci/link.sh");
        Files.createDirectories(symlink.getParent());
        Files.createSymbolicLink(symlink, temporary.resolve(owned));
        assertThatThrownBy(() -> assertProductionShell(temporary, "scripts/ci/link.sh", "120000"))
                .isInstanceOf(AssertionError.class);
    }

    private void writeFixture(String file, String source) throws Exception {
        Path path = temporary.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
    }

    private static void assertProductionShell(Path repository, String file, String mode) throws Exception {
        Path relative = Path.of(file);
        assertThat(relative.isAbsolute()).as(file).isFalse();
        assertThat(relative).as(file).isEqualTo(relative.normalize());
        assertThat(relative.toString()).as(file).endsWith(".sh");
        assertThat(PRODUCTION_ROOTS.stream().anyMatch(relative::startsWith)).as(file).isTrue();
        Path current = repository;
        for (Path component : relative) {
            current = current.resolve(component);
            assertThat(Files.isSymbolicLink(current)).as(file).isFalse();
        }
        assertThat(Files.isRegularFile(current)).as(file).isTrue();
        assertThat(mode).as(file).isEqualTo("100755");
        String source = Files.readString(current);
        assertThat(source).as(file).startsWith("#!/usr/bin/env bash\n");
        assertThat(source.lines().anyMatch(line -> line.matches("\\s*set -euo pipefail(?:\\s+#.*)?\\s*")))
                .as(file + " strict mode").isTrue();
        assertThat(source).as(file).doesNotContain("TEST_MODE", "SKIP_SECURITY_CHECK_FOR_TESTS");
    }
}
