package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PreflightTestSupport {
    static final String IMAGE_REFERENCE = "ghcr.io/0xmillennium/persefonia@sha256:" + "a".repeat(64);
    private static final Path PREFLIGHT = Path.of("../scripts/deploy/preflight.sh").toAbsolutePath();

    private PreflightTestSupport() {
    }

    static Result run(Path temporaryDirectory, Map<String, String> overrides) throws Exception {
        Path fixture = Files.createTempDirectory(temporaryDirectory, "preflight-");
        Path bin = Files.createDirectory(fixture.resolve("bin"));
        Path docker = bin.resolve("docker");
        Files.writeString(docker, """
                #!/bin/sh
                set -eu
                printf '%s\\n' "$*" >> "$PREFLIGHT_TEST_DOCKER_CALLS"
                [ "$*" = 'compose version --short' ] || exit 99
                [ "$PREFLIGHT_TEST_PLUGIN_MISSING" = false ] || exit 1
                printf '%s\\n' "$PREFLIGHT_TEST_COMPOSE_VERSION"
                """);
        assertThat(docker.toFile().setExecutable(true)).isTrue();
        Path calls = fixture.resolve("docker-calls");
        ProcessBuilder builder = new ProcessBuilder(PREFLIGHT.toString()).redirectErrorStream(true);
        String searchPath = builder.environment().get("PATH");
        builder.environment().clear();
        builder.environment().put("PATH", bin + ":" + searchPath);
        builder.environment().put("PERSEFONIA_ENV_FILE", fixture.resolve("missing.env").toString());
        builder.environment().put("PERSEFONIA_IMAGE_REF", IMAGE_REFERENCE);
        builder.environment().put("PREFLIGHT_TEST_DOCKER_CALLS", calls.toString());
        builder.environment().put("PREFLIGHT_TEST_COMPOSE_VERSION", "2.39.4");
        builder.environment().put("PREFLIGHT_TEST_PLUGIN_MISSING", "false");
        builder.environment().putAll(overrides);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.waitFor(), output, Files.exists(calls) ? Files.readString(calls) : "");
    }

    record Result(int status, String output, String dockerCalls) {}

    static RuntimeFixture runtimeFixture(Path temporaryDirectory) throws Exception {
        return new RuntimeFixture(temporaryDirectory);
    }

    static final class RuntimeFixture {
        static final List<String> SECRET_NAMES = List.of("postgres_password", "redis_password",
                "contact_rate_limit_secret", "oidc_client_secret", "cloudflare_api_token");
        static final String DIRECTORY_ACL = "user::rwx\ngroup::---\nother::---\n";
        static final String FILE_ACL = "user::rw-\ngroup::---\nmask::r--\nother::---\n";
        static final String SECRET_VALUE = "synthetic-secret-value-never-print";

        final Path stack;
        final Path bin;
        final Path acls;
        private final Map<String, String> overrides = new HashMap<>();

        RuntimeFixture(Path temporaryDirectory) throws Exception {
            Path fixture = Files.createTempDirectory(temporaryDirectory, "runtime-preflight-");
            stack = Files.createDirectory(fixture.resolve("stack"));
            bin = Files.createDirectory(fixture.resolve("bin"));
            acls = Files.createDirectory(fixture.resolve("acls"));
            // A closed PATH makes the missing-getfacl case deterministic even on hosts with acl installed.
            for (String command : List.of("dirname", "basename", "awk", "grep", "cat", "id", "stat")) {
                Files.createSymbolicLink(bin.resolve(command), Path.of("/usr/bin", command));
            }
            Path script = stack.resolve("scripts/deploy/preflight.sh");
            Files.createDirectories(script.getParent());
            Files.copy(PREFLIGHT, script);
            executable(script);
            for (String relative : List.of("compose.production.yaml", "docker/postgresql/postgresql.conf",
                    "docker/postgresql/pg_hba.conf", "docker/redis/redis.conf", "docker/redis-start.sh")) {
                Path destination = stack.resolve(relative);
                Files.createDirectories(destination.getParent());
                Files.copy(Path.of("../", relative), destination);
            }
            executable(stack.resolve("docker/redis-start.sh"));
            Files.writeString(stack.resolve(".env.production"), """
                    PERSEFONIA_PUBLIC_HOST=example.invalid
                    PERSEFONIA_TRUSTED_PROXY_CIDRS=10.0.0.0/8
                    PERSEFONIA_OIDC_ISSUER_URI=https://auth.example.invalid
                    PERSEFONIA_CONTACT_MAIL_OWNER_RECIPIENT=owner@example.invalid
                    PERSEFONIA_CONTACT_MAIL_FROM=contact@example.invalid
                    PERSEFONIA_CLOUDFLARE_ZONE_ID=synthetic-zone
                    PERSEFONIA_ADMIN_REQUIRED_OIDC_GROUP=admin
                    """);
            Files.createDirectory(secrets());
            Files.setPosixFilePermissions(secrets(), PosixFilePermissions.fromString("rwx------"));
            acl("secrets", DIRECTORY_ACL);
            for (String name : SECRET_NAMES) {
                Files.writeString(secret(name), SECRET_VALUE);
                // Extended ACL masks commonly appear as group bits in stat: file mode is not authoritative.
                Files.setPosixFilePermissions(secret(name), PosixFilePermissions.fromString("rw-r-----"));
                String users = switch (name) {
                    case "postgres_password" -> "user:70:r--\nuser:10001:r--\n";
                    case "redis_password" -> "user:999:r--\nuser:10001:r--\n";
                    default -> "user:10001:r--\n";
                };
                acl(name, FILE_ACL + users);
            }
            command("getfacl", """
                    #!/bin/sh
                    set -eu
                    while [ "$#" -gt 1 ]; do shift; done
                    cat "$PREFLIGHT_TEST_ACLS/$(basename -- "$1")"
                    """);
            command("docker", """
                    #!/bin/sh
                    set -eu
                    printf '%s\\n' "$*" >> "$PREFLIGHT_TEST_DOCKER_CALLS"
                    case "$*" in
                        'compose version --short') printf '%s\\n' 2.39.4 ;;
                        'network inspect backnet'|'network inspect frontnet') exit 0 ;;
                        'compose --env-file '*" config --quiet") exit 0 ;;
                        *) exit 99 ;;
                    esac
                    """);
        }

        Path secrets() { return stack.resolve("secrets"); }
        Path secret(String name) { return secrets().resolve(name); }

        void acl(String name, String acl) throws Exception {
            Files.writeString(acls.resolve(name), acl);
        }

        void wrongOwnership(Path path, String field) throws Exception {
            // Only ownership-negative cases fake stat; every other check uses real filesystem metadata.
            Path stat = bin.resolve("stat");
            Files.delete(stat);
            command("stat", """
                    #!/bin/sh
                    set -eu
                    if [ "$2" = "$PREFLIGHT_TEST_STAT_FIELD" ] && [ "$4" = "$PREFLIGHT_TEST_STAT_PATH" ]; then
                        printf '%s\\n' 4294967294
                    else
                        exec /usr/bin/stat "$@"
                    fi
                    """);
            overrides.put("PREFLIGHT_TEST_STAT_FIELD", field);
            overrides.put("PREFLIGHT_TEST_STAT_PATH", path.toString());
        }

        Result run() throws Exception {
            Path calls = stack.getParent().resolve("docker-calls");
            ProcessBuilder builder = new ProcessBuilder(stack.resolve("scripts/deploy/preflight.sh").toString())
                    .redirectErrorStream(true);
            builder.environment().clear();
            builder.environment().put("PATH", bin.toString());
            builder.environment().put("PERSEFONIA_IMAGE_REF", IMAGE_REFERENCE);
            builder.environment().put("PREFLIGHT_TEST_DOCKER_CALLS", calls.toString());
            builder.environment().put("PREFLIGHT_TEST_ACLS", acls.toString());
            builder.environment().putAll(overrides);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new Result(process.waitFor(), output, Files.exists(calls) ? Files.readString(calls) : "");
        }

        private void command(String name, String script) throws Exception {
            Path command = bin.resolve(name);
            Files.writeString(command, script);
            executable(command);
        }

        private static void executable(Path file) {
            assertThat(file.toFile().setExecutable(true)).isTrue();
        }
    }
}
