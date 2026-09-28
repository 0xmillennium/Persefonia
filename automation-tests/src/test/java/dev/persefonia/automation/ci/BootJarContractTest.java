package dev.persefonia.automation.ci;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.CommandResult;
import dev.persefonia.automation.support.CommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BootJarContractTest {
    @TempDir Path temporary;
    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void stagesByteIdenticalValidatedJarAndPreservesUnrelatedOutput() throws Exception {
        Path jar = jar(true, true);
        Path output = temporary.resolve("output");
        Files.createDirectories(output);
        Files.writeString(output.resolve("keep.txt"), "unrelated");
        CommandResult result = run(jar, output);
        result.requireSuccess();
        assertThat(Files.mismatch(jar, output.resolve("persefonia.jar"))).isEqualTo(-1);
        assertThat(Files.readString(output.resolve("keep.txt"))).isEqualTo("unrelated");
        new CommandRunner(output, temporary.resolve("home"), Duration.ofSeconds(10), Map.of())
                .run("sha256sum", "--check", "persefonia.jar.sha256").requireSuccess();
    }

    @Test
    void rejectsMissingRuntimeLibraryMigrationOrBuildIdentity() throws Exception {
        assertThat(run(jar(false, true), temporary.resolve("out-a")).status()).isNotZero();
        assertThat(run(jar(true, false), temporary.resolve("out-b")).status()).isNotZero();
        assertThat(run(jar(true, true, false), temporary.resolve("out-missing-migration")).status()).isNotZero();
        Path empty = temporary.resolve("empty.jar");
        Files.writeString(empty, "");
        assertThat(run(empty, temporary.resolve("out-c")).status()).isNotZero();
    }

    private Path jar(boolean libraries, boolean identity) throws Exception {
        return jar(libraries, identity, true);
    }

    private Path jar(boolean libraries, boolean identity, boolean migrationsPresent) throws Exception {
        Path contents = Files.createTempDirectory(temporary, "jar-contents-");
        Path lib = contents.resolve("BOOT-INF/lib");
        Files.createDirectories(lib);
        if (libraries) {
            for (String name : new String[] {"spring-boot-flyway", "flyway-core", "flyway-database-postgresql"}) {
                Files.writeString(lib.resolve(name + "-1.jar"), "fixture");
            }
        }
        Path migrations = repository.resolve("app/src/main/resources/db/migration");
        if (migrationsPresent) {
            try (var paths = Files.list(migrations)) {
                for (Path source : paths.filter(path -> path.toString().endsWith(".sql")).toList()) {
                    Path destination = contents.resolve("BOOT-INF/classes/db/migration").resolve(source.getFileName());
                    Files.createDirectories(destination.getParent());
                    Files.writeString(destination, "fixture");
                }
            }
        }
        Path metadata = contents.resolve("META-INF/build-info.properties");
        Files.createDirectories(metadata.getParent());
        Files.writeString(metadata, identity ? "build.name=persefonia\nbuild.version=0.1.0\n" : "build.name=other\n");
        Path jar = Files.createTempFile(temporary, "synthetic-", ".jar");
        Files.delete(jar);
        new CommandRunner(contents, temporary.resolve("home"), Duration.ofSeconds(10), Map.of())
                .run("zip", "-qr", jar.toString(), ".").requireSuccess();
        return jar;
    }

    private CommandResult run(Path jar, Path output) throws Exception {
        return new CommandRunner(repository, temporary.resolve("home"), Duration.ofSeconds(10), Map.of())
                .run(repository.resolve("scripts/ci/verify-bootjar.sh").toString(), jar.toString(), output.toString());
    }
}
