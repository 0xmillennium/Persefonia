package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeliveryHandoffContractTest {
    private static final Path WRITER = Path.of("../scripts/release/write-delivery-handoff.sh").toAbsolutePath();
    private static final Path VERIFIER = Path.of("../scripts/deploy/verify-delivery-handoff.sh").toAbsolutePath();
    private static final String SOURCE_SHA = "a".repeat(40);
    private static final String IMAGE = "ghcr.io/0xmillennium/persefonia";
    private static final String DIGEST = "sha256:" + "b".repeat(64);
    private static final String REPOSITORY = "0xmillennium/Persefonia";
    private static final String RUN_ID = "12345";
    private static final String RUN_ATTEMPT = "2";

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesExactlyEightStableRecordsAndVerifierEmitsOnlyTheExpectedDigest() throws Exception {
        Path file = temporaryDirectory.resolve("handoff.txt");

        Result written = write(file, SOURCE_SHA, IMAGE, DIGEST, RUN_ID, RUN_ATTEMPT);
        assertThat(written.status()).isZero();
        assertThat(written.stdout()).isEmpty();
        assertThat(Files.readString(file)).isEqualTo(validHandoff());

        Result verified = verify(file, SOURCE_SHA, RUN_ID, RUN_ATTEMPT, REPOSITORY);
        assertThat(verified.status()).isZero();
        assertThat(verified.stdout()).isEqualTo("expected_image_digest=" + DIGEST + "\n");
    }

    @Test
    void rejectsInvalidWriterArgumentsWithoutCreatingFinalOrTemporaryFiles() throws Exception {
        List<String[]> invalid = List.of(
                new String[] {"abc", IMAGE, DIGEST, RUN_ID, RUN_ATTEMPT},
                new String[] {SOURCE_SHA, "ghcr.io/Other/repository", DIGEST, RUN_ID, RUN_ATTEMPT},
                new String[] {SOURCE_SHA, IMAGE + ":latest", DIGEST, RUN_ID, RUN_ATTEMPT},
                new String[] {SOURCE_SHA, IMAGE + "@" + DIGEST, DIGEST, RUN_ID, RUN_ATTEMPT},
                new String[] {SOURCE_SHA, IMAGE, "sha256:short", RUN_ID, RUN_ATTEMPT},
                new String[] {SOURCE_SHA, IMAGE, DIGEST, "0", RUN_ATTEMPT},
                new String[] {SOURCE_SHA, IMAGE, DIGEST, RUN_ID, "-1"});
        for (int index = 0; index < invalid.size(); index++) {
            Path directory = Files.createDirectory(temporaryDirectory.resolve("invalid-" + index));
            Path file = directory.resolve("handoff.txt");
            String[] values = invalid.get(index);
            Result result = write(file, values[0], values[1], values[2], values[3], values[4]);
            assertThat(result.status()).as("case %s", index).isNotZero();
            assertThat(result.stdout()).isEmpty();
            assertThat(file).doesNotExist();
            try (var files = Files.list(directory)) {
                assertThat(files.toList()).isEmpty();
            }
        }
    }

    @Test
    void rejectsMalformedOrInconsistentHandoffWithoutSuccessfulOutput() throws Exception {
        String valid = validHandoff();
        List<String> invalid = List.of(
                valid.replace("format_version=1", "format_version=2"),
                valid.replace("delivery_run_id=" + RUN_ID + "\n", ""),
                valid + "source_sha=" + SOURCE_SHA + "\n",
                valid + "unknown_key=value\n",
                valid.replace("format_version=1", "malformed"),
                valid.replace("delivery_run_id=" + RUN_ID, "delivery_run_id=999"),
                valid.replace("delivery_run_attempt=" + RUN_ATTEMPT, "delivery_run_attempt=3"),
                valid.replace("source_sha=" + SOURCE_SHA, "source_sha=" + "c".repeat(40)),
                valid.replace("image_name=" + IMAGE, "image_name=ghcr.io/other/repository"),
                valid.replace("image_digest=" + DIGEST, "image_digest=sha256:short"),
                valid.replace("image_reference=" + IMAGE + "@" + DIGEST,
                        "image_reference=" + IMAGE + "@sha256:" + "c".repeat(64)),
                valid.replace("source_alias=" + IMAGE + ":sha-" + SOURCE_SHA,
                        "source_alias=" + IMAGE + ":sha-" + "c".repeat(40)),
                valid + "\n",
                valid.replace("format_version=1\n", "format_version=1\r\n"),
                valid.substring(0, valid.length() - 1));
        for (int index = 0; index < invalid.size(); index++) {
            Path file = temporaryDirectory.resolve("invalid-file-" + index);
            Files.writeString(file, invalid.get(index));
            Result result = verify(file, SOURCE_SHA, RUN_ID, RUN_ATTEMPT, REPOSITORY);
            assertThat(result.status()).as("case %s", index).isNotZero();
            assertThat(result.stdout()).isEmpty();
        }
        Path nulFile = temporaryDirectory.resolve("nul-file");
        Files.write(nulFile, ("format_version=1\0" + valid.substring("format_version=1".length()))
                .getBytes(StandardCharsets.UTF_8));
        Result nulResult = verify(nulFile, SOURCE_SHA, RUN_ID, RUN_ATTEMPT, REPOSITORY);
        assertThat(nulResult.status()).isNotZero();
        assertThat(nulResult.stdout()).isEmpty();
    }

    @Test
    void treatsShellLikeHandoffValuesOnlyAsData() throws Exception {
        Path marker = temporaryDirectory.resolve("executed");
        Path file = temporaryDirectory.resolve("malicious-handoff.txt");
        Files.writeString(file, validHandoff().replace("source_sha=" + SOURCE_SHA,
                "source_sha=$(touch " + marker + ")"));

        Result result = verify(file, SOURCE_SHA, RUN_ID, RUN_ATTEMPT, REPOSITORY);
        assertThat(result.status()).isNotZero();
        assertThat(result.stdout()).isEmpty();
        assertThat(marker).doesNotExist();
    }

    private Result write(Path file, String source, String image, String digest, String runId, String attempt)
            throws Exception {
        return run(WRITER.toString(), file.toString(), source, image, digest, runId, attempt);
    }

    private Result verify(Path file, String source, String runId, String attempt, String repository)
            throws Exception {
        return run(VERIFIER.toString(), file.toString(), source, runId, attempt, repository);
    }

    private static Result run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.waitFor(), stdout, stderr);
    }

    private static String validHandoff() {
        return "format_version=1\n"
                + "delivery_run_id=" + RUN_ID + "\n"
                + "delivery_run_attempt=" + RUN_ATTEMPT + "\n"
                + "source_sha=" + SOURCE_SHA + "\n"
                + "image_name=" + IMAGE + "\n"
                + "image_digest=" + DIGEST + "\n"
                + "image_reference=" + IMAGE + "@" + DIGEST + "\n"
                + "source_alias=" + IMAGE + ":sha-" + SOURCE_SHA + "\n";
    }

    private record Result(int status, String stdout, String stderr) {}
}
