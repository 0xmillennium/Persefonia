package dev.persefonia.app.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

class LocalRedisAclIntegrationTest {
    private static final String IMAGE = "redis:8.10-alpine@sha256:becdda6c7f4b3fb42e42fd7f120bbf5c54c4caaaf16f26da24e4563d2c1f0576";
    private static final String PASSWORD = "fake-redis-acl-test-password";
    private static final String PREFIX = "persefonia:local:rate-limit";

    @TempDir Path temporary;

    @Test
    void passwordlessDefaultUserCanOnlyPing() throws Exception {
        try (GenericContainer<?> redis = startRedis()) {
            assertThat(command(redis, "ping")).isEqualTo("PONG");
            for (String[] denied : new String[][] {
                {"get", "any-key"}, {"set", "any-key", "value"},
                {"incr", "any-key"}, {"pexpire", "any-key", "1000"},
                {"flushall"}, {"flushdb"}, {"config", "get", "save"},
                {"acl", "list"}, {"client", "list"}, {"shutdown"}
            }) {
                assertThat(command(redis, denied)).as(String.join(" ", denied)).startsWith("NOPERM ");
            }
        }
    }

    @Test
    void namedApplicationUserCanOnlyUseItsRateLimitNamespaceAndCommands() throws Exception {
        try (GenericContainer<?> redis = startRedis()) {
            assertThat(applicationCommand(redis, "incr", PREFIX + ":contact:test")).isEqualTo("1");
            assertThat(applicationCommand(redis, "pexpire", PREFIX + ":contact:test", "60000")).isEqualTo("1");
            assertThat(applicationCommand(redis, "incr", "outside:test")).startsWith("NOPERM ");
            assertThat(applicationCommand(redis, "flushall")).startsWith("NOPERM ");
            assertThat(applicationCommand(redis, "ping")).startsWith("NOPERM ");
        }
    }

    private GenericContainer<?> startRedis() throws Exception {
        Path secret = temporary.resolve("password");
        Path acl = temporary.resolve("users.acl");
        Files.writeString(secret, PASSWORD);
        ProcessBuilder generator = new ProcessBuilder(
                        "sh", "scripts/local-redis-entrypoint.sh", "--generate-only",
                        secret.toString(), acl.toString())
                .directory(Path.of("..").toFile())
                .inheritIO();
        generator.environment().put("PERSEFONIA_REDIS_USERNAME", "persefonia");
        generator.environment().put("PERSEFONIA_REDIS_KEY_PREFIX", PREFIX);
        Process generation = generator.start();
        assertThat(generation.waitFor()).isZero();

        GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withCopyFileToContainer(MountableFile.forHostPath(acl, 0644), "/tmp/persefonia-users.acl")
                .withCommand("redis-server", "--aclfile", "/tmp/persefonia-users.acl", "--appendonly", "no", "--save", "")
                .withExposedPorts(6379);
        redis.start();
        return redis;
    }

    private static String command(GenericContainer<?> redis, String... command) throws Exception {
        String[] args = new String[command.length + 2];
        args[0] = "redis-cli";
        args[1] = "--raw";
        System.arraycopy(command, 0, args, 2, command.length);
        return redis.execInContainer(args).getStdout().trim();
    }

    private static String applicationCommand(GenericContainer<?> redis, String... command) throws Exception {
        String[] args = new String[command.length + 6];
        args[0] = "redis-cli";
        args[1] = "--raw";
        args[2] = "--user";
        args[3] = "persefonia";
        args[4] = "--pass";
        args[5] = PASSWORD;
        System.arraycopy(command, 0, args, 6, command.length);
        return redis.execInContainer(args).getStdout().trim();
    }
}
