package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SshTargetTrustContractTest {
    private static final Path VERIFIER = Path.of("../scripts/deploy/verify-ssh-target.sh").toAbsolutePath();
    private static final String RESOURCE_ROOT = "/architecture/rc-deployment/";
    private static final String FINGERPRINT = "SHA256:" + "A".repeat(43);
    private static final String RECORD = "[rc.example.test]:2222 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIFakeKey\n";
    private static final String PROBE = "PERSEFONIA_SSH_TARGET_V1\n1001\ndeploy_user\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void requiresExactlyFiveArguments() throws Exception {
        Process process = new ProcessBuilder(VERIFIER.toString()).start();
        assertThat(process.waitFor()).isNotZero();
        assertThat(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void acceptsPinnedHostKeyAndExpectedNonRootPrincipalWithStrictSshPolicy() throws Exception {
        Result result = verify("rc.example.test", "2222", "deploy_user", FINGERPRINT, "valid", Map.of());

        assertThat(result.status()).isZero();
        assertThat(result.stdout()).isEmpty();
        assertThat(result.scanArgs()).contains("-T\n10\n", "-p\n2222\n", "-t\ned25519\n");
        assertThat(result.sshArgs()).contains(
                "-F\n/dev/null\n", "-p\n2222\n", "-i\n", "BatchMode=yes", "IdentitiesOnly=yes",
                "PubkeyAuthentication=yes", "PreferredAuthentications=publickey",
                "PasswordAuthentication=no", "KbdInteractiveAuthentication=no",
                "StrictHostKeyChecking=yes", "UserKnownHostsFile=", "GlobalKnownHostsFile=/dev/null",
                "HostKeyAlgorithms=ssh-ed25519", "VerifyHostKeyDNS=no", "UpdateHostKeys=no",
                "ClearAllForwardings=yes", "ForwardAgent=no", "RequestTTY=no", "PermitLocalCommand=no",
                "ConnectTimeout=10", "ConnectionAttempts=1", "IdentityAgent=none",
                "ProxyCommand=none", "ProxyJump=none", "deploy_user@rc.example.test",
                "printf \"PERSEFONIA_SSH_TARGET_V1\\n\"; id -u; id -un");
        assertThat(result.knownHosts()).isEqualTo(RECORD);
        assertThat(result.fingerprintedRecord()).isEqualTo(RECORD);
    }

    @Test
    void acceptsRawIpv6AndNormalizesDecimalPortBeforeScanning() throws Exception {
        String record = "2001:db8::1 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIFakeKey\n";
        Result result = verify("2001:db8::1", "00022", "deploy_user", FINGERPRINT, "valid",
                Map.of("FAKE_SCAN_RECORD", record));

        assertThat(result.status()).isZero();
        assertThat(result.scanArgs()).contains("-p\n22\n");
        assertThat(result.sshArgs()).contains("-p\n22\n", "deploy_user@2001:db8::1");
        assertThat(result.knownHosts()).isEqualTo(record);
    }

    @Test
    void rejectsMalformedInputsAndKeysBeforeScanning() throws Exception {
        List<String[]> cases = List.of(
                new String[] {"", "2222", "deploy_user", FINGERPRINT, "valid"},
                new String[] {"bad host", "2222", "deploy_user", FINGERPRINT, "valid"},
                new String[] {"ssh://host", "2222", "deploy_user", FINGERPRINT, "valid"},
                new String[] {"user@host", "2222", "deploy_user", FINGERPRINT, "valid"},
                new String[] {"-host", "2222", "deploy_user", FINGERPRINT, "valid"},
                new String[] {"host:2222", "2222", "deploy_user", FINGERPRINT, "valid"},
                new String[] {"rc.example.test", "", "deploy_user", FINGERPRINT, "valid"},
                new String[] {"rc.example.test", "abc", "deploy_user", FINGERPRINT, "valid"},
                new String[] {"rc.example.test", "0", "deploy_user", FINGERPRINT, "valid"},
                new String[] {"rc.example.test", "65536", "deploy_user", FINGERPRINT, "valid"},
                new String[] {"rc.example.test", "2222", "bad@user", FINGERPRINT, "valid"},
                new String[] {"rc.example.test", "2222", "root", FINGERPRINT, "valid"},
                new String[] {"rc.example.test", "2222", "deploy_user", "bad", "valid"},
                new String[] {"rc.example.test", "2222", "deploy_user", "MD5:" + "a".repeat(47), "valid"},
                new String[] {"rc.example.test", "2222", "deploy_user", "SHA256:short", "valid"},
                new String[] {"rc.example.test", "2222", "deploy_user", FINGERPRINT, "missing"},
                new String[] {"rc.example.test", "2222", "deploy_user", FINGERPRINT, "symlink"},
                new String[] {"rc.example.test", "2222", "deploy_user", FINGERPRINT, "empty"},
                new String[] {"rc.example.test", "2222", "deploy_user", FINGERPRINT, "public"});
        for (String[] values : cases) {
            Result result = verify(values[0], values[1], values[2], values[3], values[4], Map.of());
            assertThat(result.status()).as(List.of(values).toString()).isNotZero();
            assertThat(result.stdout()).isEmpty();
            assertThat(result.scanArgs()).isEmpty();
            assertThat(result.sshArgs()).isEmpty();
        }
        Result parseFailure = verify("rc.example.test", "2222", "deploy_user", FINGERPRINT,
                "valid", Map.of("FAKE_KEY_PARSE_STATUS", "1"));
        assertThat(parseFailure.status()).isNotZero();
        assertThat(parseFailure.scanArgs()).isEmpty();
    }

    @Test
    void rejectsScanAndHostIdentityFailuresBeforeSsh() throws Exception {
        List<Map<String, String>> failures = List.of(
                Map.of("FAKE_KEYSCAN_STATUS", "1"),
                Map.of("FAKE_SCAN_RECORD", ""),
                Map.of("FAKE_SCAN_RECORD", "\n"),
                Map.of("FAKE_SCAN_RECORD", "garbage\n"),
                Map.of("FAKE_SCAN_RECORD", "[rc.example.test]:2222 ssh-rsa AAAA\n"),
                Map.of("FAKE_SCAN_RECORD", RECORD + "[rc.example.test]:2222 ssh-ed25519 AnotherKey\n"),
                Map.of("FAKE_PRESENTED_FINGERPRINT", "SHA256:" + "B".repeat(43)),
                Map.of("FAKE_FINGERPRINT_STATUS", "1"));
        for (Map<String, String> failure : failures) {
            Result result = verify("rc.example.test", "2222", "deploy_user", FINGERPRINT, "valid", failure);
            assertThat(result.status()).as(failure.toString()).isNotZero();
            assertThat(result.stdout()).isEmpty();
            assertThat(result.sshArgs()).isEmpty();
        }
    }

    @Test
    void rejectsSshFailureAndEveryMalformedRemotePrincipalResponse() throws Exception {
        List<Map<String, String>> failures = List.of(
                Map.of("FAKE_SSH_STATUS", "255"),
                Map.of("FAKE_SSH_STDOUT", "WRONG\n1001\ndeploy_user\n"),
                Map.of("FAKE_SSH_STDOUT", "1001\ndeploy_user\n"),
                Map.of("FAKE_SSH_STDOUT", PROBE + "extra\n"),
                Map.of("FAKE_SSH_STDOUT", "PERSEFONIA_SSH_TARGET_V1\nabc\ndeploy_user\n"),
                Map.of("FAKE_SSH_STDOUT", "PERSEFONIA_SSH_TARGET_V1\n0\ndeploy_user\n"),
                Map.of("FAKE_SSH_STDOUT", "PERSEFONIA_SSH_TARGET_V1\n1001\nother\n"),
                Map.of("FAKE_SSH_STDOUT", "PERSEFONIA_SSH_TARGET_V1\n1001\n"));
        for (Map<String, String> failure : failures) {
            Result result = verify("rc.example.test", "2222", "deploy_user", FINGERPRINT, "valid", failure);
            assertThat(result.status()).as(failure.toString()).isNotZero();
            assertThat(result.stdout()).isEmpty();
        }
    }

    private Result verify(String host, String port, String user, String fingerprint, String keyKind,
                          Map<String, String> overrides) throws Exception {
        Path fixture = Files.createTempDirectory(temporaryDirectory, "ssh-");
        Path bin = Files.createDirectory(fixture.resolve("bin"));
        for (String command : List.of("ssh-keyscan", "ssh-keygen", "ssh")) {
            try (InputStream source = getClass().getResourceAsStream(RESOURCE_ROOT + "fake-" + command + ".sh")) {
                assertThat(source).isNotNull();
                Path executable = bin.resolve(command);
                Files.copy(source, executable);
                assertThat(executable.toFile().setExecutable(true)).isTrue();
            }
        }
        Path key = fixture.resolve("key");
        if (!keyKind.equals("missing")) {
            if (keyKind.equals("symlink")) {
                Path target = fixture.resolve("target");
                Files.writeString(target, "synthetic-key");
                Files.createSymbolicLink(key, target);
            } else {
                Files.writeString(key, keyKind.equals("empty") ? "" : "synthetic-key");
                Files.setPosixFilePermissions(key, PosixFilePermissions.fromString(
                        keyKind.equals("public") ? "rw-r--r--" : "rw-------"));
            }
        }
        Path scanArgs = fixture.resolve("scan-args");
        Path keygenArgs = fixture.resolve("keygen-args");
        Path sshArgs = fixture.resolve("ssh-args");
        Path knownHosts = fixture.resolve("known-hosts");
        Path fingerprintedRecord = fixture.resolve("fingerprinted-record");
        ProcessBuilder processBuilder = new ProcessBuilder(VERIFIER.toString(), host, port, user,
                fingerprint, key.toString());
        Map<String, String> environment = processBuilder.environment();
        environment.put("PATH", bin + ":" + environment.get("PATH"));
        environment.put("FAKE_KEYSCAN_ARGS", scanArgs.toString());
        environment.put("FAKE_KEYGEN_ARGS", keygenArgs.toString());
        environment.put("FAKE_KEYGEN_RECORD", fingerprintedRecord.toString());
        environment.put("FAKE_SSH_ARGS", sshArgs.toString());
        environment.put("FAKE_SSH_KNOWN_HOSTS", knownHosts.toString());
        environment.put("FAKE_SCAN_RECORD", RECORD);
        environment.put("FAKE_PRESENTED_FINGERPRINT", FINGERPRINT);
        environment.put("FAKE_SSH_STDOUT", PROBE);
        environment.putAll(overrides);
        Process process = processBuilder.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int status = process.waitFor();
        return new Result(status, stdout, stderr, read(scanArgs), read(keygenArgs), read(sshArgs),
                read(knownHosts), read(fingerprintedRecord));
    }

    private static String read(Path file) throws Exception {
        return Files.exists(file) ? Files.readString(file) : "";
    }

    private record Result(int status, String stdout, String stderr, String scanArgs,
                          String keygenArgs, String sshArgs, String knownHosts, String fingerprintedRecord) {}
}
