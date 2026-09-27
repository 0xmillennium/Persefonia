package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RcDeploymentInvocationContractTest {
    private static final Path SCRIPT = Path.of("../scripts/deploy/run-rc-deployment.sh").toAbsolutePath();
    private static final Path PAYLOAD = Path.of("../scripts/deploy/rc-host-deploy.sh");
    private static final String SOURCE = "a".repeat(40);
    private static final String IMAGE = "ghcr.io/0xmillennium/persefonia@sha256:" + "b".repeat(64);
    private static final String PROTOCOL = "format_version=1\nsource_sha=" + SOURCE + "\nimage_reference=" + IMAGE
            + "\npostgres_container_id=" + "1".repeat(64) + "\nredis_container_id=" + "2".repeat(64)
            + "\napp_container_id=" + "3".repeat(64) + "\napp_image_id=sha256:" + "4".repeat(64)
            + "\napp_health=healthy\n";

    @TempDir Path temp;

    @Test
    void streamsExactPayloadThroughStrictSshAndNormalizesProtocol() throws Exception {
        Result result = invoke(List.of(), PROTOCOL, Map.of());
        assertThat(result.status()).isZero();
        assertThat(result.stdout()).isEqualTo(PROTOCOL);
        assertThat(result.stdin()).isEqualTo(Files.readString(PAYLOAD));
        assertThat(result.sshArgs()).contains("-F\n/dev/null\n", "-p\n2222\n", "BatchMode=yes",
                "StrictHostKeyChecking=yes", "HostKeyAlgorithms=ssh-ed25519", "PasswordAuthentication=no",
                "KbdInteractiveAuthentication=no", "ForwardAgent=no", "RequestTTY=no",
                "ProxyCommand=none", "ProxyJump=none", "ConnectionAttempts=1", "ConnectTimeout=10",
                "UserKnownHostsFile=", "deploy_user@rc.example.test", "bash -s -- " + SOURCE + " " + IMAGE);
        assertThat(result.sshArgs()).doesNotContain("ssh-keyscan", "scp", "rsync");
    }

    @Test
    void rejectsMalformedLocalInputsBeforeSsh() throws Exception {
        for (int count : List.of(0, 6, 8)) {
            Path marker = temp.resolve("invalid-count-" + count);
            var args = new ArrayList<String>();
            args.add(SCRIPT.toString());
            for (int i = 0; i < count; i++) args.add("x");
            Process process = new ProcessBuilder(args).start();
            process.getInputStream().readAllBytes();
            process.getErrorStream().readAllBytes();
            assertThat(process.waitFor()).as(marker.toString()).isNotZero();
        }
        for (String[] change : List.of(
                new String[] {"0", "bad host"}, new String[] {"0", "user@host"},
                new String[] {"1", "0"}, new String[] {"1", "65536"},
                new String[] {"2", "root"}, new String[] {"2", "bad@user"},
                new String[] {"5", "A".repeat(40)}, new String[] {"5", "bad"},
                new String[] {"6", "ghcr.io/0xmillennium/persefonia:latest"},
                new String[] {"6", "ghcr.io/0xmillennium/persefonia:tag@sha256:" + "b".repeat(64)},
                new String[] {"6", "ghcr.io/other/persefonia@sha256:" + "b".repeat(64)},
                new String[] {"6", IMAGE.toUpperCase()}, new String[] {"6", IMAGE + "\n"})) {
            Result result = invoke(List.<String[]>of(change), PROTOCOL, Map.of());
            assertThat(result.status()).as(List.of(change).toString()).isNotZero();
            assertThat(result.sshArgs()).isEmpty();
        }
        for (String kind : List.of("missing-key", "symlink-key", "public-key", "missing-hosts",
                "symlink-hosts", "public-hosts", "wrong-hosts", "extra-hosts", "spaces-hosts")) {
            Result result = invoke(List.of(), PROTOCOL, Map.of("FIXTURE_KIND", kind));
            assertThat(result.status()).as(kind).isNotZero();
            assertThat(result.sshArgs()).isEmpty();
        }
    }

    @Test
    void rejectsUntrustedRemoteOutputAndSshFailures() throws Exception {
        for (String protocol : List.of(
                PROTOCOL.substring(0, PROTOCOL.length() - 1), PROTOCOL + "extra=bad\n",
                PROTOCOL.replace("format_version=1", "format_version=2"),
                PROTOCOL.replace("source_sha=" + SOURCE, "source_sha=" + "c".repeat(40)),
                PROTOCOL.replace("image_reference=" + IMAGE, "image_reference=wrong"),
                PROTOCOL.replace("postgres_container_id=" + "1".repeat(64), "postgres_container_id=wrong"),
                PROTOCOL.replace("app_image_id=sha256:" + "4".repeat(64), "app_image_id=wrong"),
                PROTOCOL.replace("app_health=healthy", "app_health=unhealthy"),
                PROTOCOL.replace("app_health=healthy\n", ""),
                PROTOCOL.replace("format_version=1", "unknown=1"),
                PROTOCOL.replace("app_health=healthy", "format_version=1"),
                PROTOCOL.replace("\n", "\r\n"), PROTOCOL.replace("healthy", "heal\0thy"))) {
            Result result = invoke(List.of(), protocol, Map.of());
            assertThat(result.status()).as(protocol).isNotZero();
            assertThat(result.stdout()).isEmpty();
        }
        Result failed = invoke(List.of(), PROTOCOL, Map.of("FAKE_RC_SSH_STATUS", "255"));
        assertThat(failed.status()).isNotZero();
        assertThat(failed.stdout()).isEmpty();
    }

    private Result invoke(List<String[]> changes, String protocol, Map<String, String> overrides) throws Exception {
        Path dir = Files.createTempDirectory(temp, "invocation-");
        Path bin = Files.createDirectory(dir.resolve("bin"));
        Path ssh = bin.resolve("ssh");
        Files.copy(Path.of("src/test/resources/architecture/rc-deployment/fake-rc-deployment-ssh.sh"), ssh);
        ssh.toFile().setExecutable(true);
        Path key = dir.resolve("key");
        Path hosts = dir.resolve("hosts");
        Files.writeString(key, "synthetic");
        Files.writeString(hosts, "[rc.example.test]:2222 ssh-ed25519 AAAABBBB\n");
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-------"));
        Files.setPosixFilePermissions(hosts, PosixFilePermissions.fromString("rw-------"));
        String kind = overrides.getOrDefault("FIXTURE_KIND", "");
        switch (kind) {
            case "missing-key" -> Files.delete(key);
            case "symlink-key" -> { Files.move(key, dir.resolve("target")); Files.createSymbolicLink(key, dir.resolve("target")); }
            case "public-key" -> Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-r--r--"));
            case "missing-hosts" -> Files.delete(hosts);
            case "symlink-hosts" -> { Files.move(hosts, dir.resolve("target")); Files.createSymbolicLink(hosts, dir.resolve("target")); }
            case "public-hosts" -> Files.setPosixFilePermissions(hosts, PosixFilePermissions.fromString("rw-r--r--"));
            case "wrong-hosts" -> Files.writeString(hosts, "wrong ssh-ed25519 AAAABBBB\n");
            case "extra-hosts" -> Files.writeString(hosts, "[rc.example.test]:2222 ssh-ed25519 AAAABBBB\nextra\n");
            case "spaces-hosts" -> Files.writeString(hosts, "[rc.example.test]:2222  ssh-ed25519 AAAABBBB\n");
            default -> { }
        }
        List<String> arguments = new ArrayList<>(List.of("rc.example.test", "2222", "deploy_user",
                key.toString(), hosts.toString(), SOURCE, IMAGE));
        for (String[] change : changes) arguments.set(Integer.parseInt(change[0]), change[1]);
        arguments.add(0, SCRIPT.toString());
        ProcessBuilder builder = new ProcessBuilder(arguments);
        Path args = dir.resolve("ssh-args"), stdin = dir.resolve("stdin"), output = dir.resolve("remote-stdout");
        Files.write(output, protocol.getBytes(StandardCharsets.UTF_8));
        builder.environment().put("PATH", bin + ":" + builder.environment().get("PATH"));
        builder.environment().put("FAKE_RC_SSH_ARGS", args.toString());
        builder.environment().put("FAKE_RC_SSH_STDIN", stdin.toString());
        builder.environment().put("FAKE_RC_SSH_STDOUT_FILE", output.toString());
        builder.environment().putAll(overrides);
        Process process = builder.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.getErrorStream().readAllBytes();
        return new Result(process.waitFor(), stdout, read(args), read(stdin));
    }

    private static String read(Path file) throws Exception { return Files.exists(file) ? Files.readString(file) : ""; }
    private record Result(int status, String stdout, String sshArgs, String stdin) {}
}
