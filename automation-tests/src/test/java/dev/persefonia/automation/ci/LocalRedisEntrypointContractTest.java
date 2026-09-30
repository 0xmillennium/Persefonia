package dev.persefonia.automation.ci;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.automation.support.CommandResult;
import dev.persefonia.automation.support.CommandRunner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRedisEntrypointContractTest {
    private static final String USERNAME = "persefonia";
    private static final String PREFIX = "persefonia:local:rate-limit";
    private static final String FAKE_PASSWORD = "fake-local-test-password";
    private static final Path SCRIPT = Path.of("../scripts/local-redis-entrypoint.sh").toAbsolutePath();

    @TempDir Path temporary;

    @Test
    void generatesHashedNamespaceRestrictedAclWithoutAdministrativeCommands() throws Exception {
        Path secret = temporary.resolve("secret");
        Path acl = temporary.resolve("users.acl");
        Files.writeString(secret, FAKE_PASSWORD + "\n");

        assertThat(generate(secret, acl, USERNAME, PREFIX).status()).isZero();
        String content = Files.readString(acl);
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(FAKE_PASSWORD.getBytes(StandardCharsets.UTF_8));
        assertThat(content).isEqualTo("user default off\nuser persefonia on #"
                + HexFormat.of().formatHex(digest)
                + " ~persefonia:local:rate-limit:* +hello +ping +client|setinfo +incr +pexpire\n");
        assertThat(content).doesNotContain(FAKE_PASSWORD, "~*", "+@all", "+flushall", "+config", "+acl");
        assertThat(Files.getPosixFilePermissions(acl))
                .isEqualTo(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }

    @Test
    void rejectsMissingOrUnsafeInputsWithoutWritingAclOrRevealingPassword() throws Exception {
        Path secret = temporary.resolve("secret");
        Files.writeString(secret, FAKE_PASSWORD);
        for (Map.Entry<String, String> invalid : Map.of(
                "missing username", "",
                "unsafe username", "admin *",
                "wildcard username", "foo*bar").entrySet()) {
            Path acl = temporary.resolve(invalid.getKey().replace(' ', '-') + ".acl");
            Result result = generate(secret, acl, invalid.getValue(), PREFIX);
            assertThat(result.status()).as(invalid.getKey()).isNotZero();
            assertThat(result.output()).doesNotContain(FAKE_PASSWORD);
            assertThat(acl).doesNotExist();
        }
        for (String prefix : new String[] {"", "~*", "rate limit", "prefix:*"}) {
            Path acl = temporary.resolve("unsafe-prefix.acl");
            Result result = generate(secret, acl, USERNAME, prefix);
            assertThat(result.status()).as(prefix).isNotZero();
            assertThat(result.output()).doesNotContain(FAKE_PASSWORD);
            assertThat(acl).doesNotExist();
        }
        Path acl = temporary.resolve("missing.acl");
        assertThat(generate(temporary.resolve("missing-secret"), acl, USERNAME, PREFIX).status()).isNotZero();
        Files.writeString(secret, "");
        assertThat(generate(secret, acl, USERNAME, PREFIX).status()).isNotZero();
        assertThat(acl).doesNotExist();
    }

    private Result generate(Path secret, Path acl, String username, String prefix) throws Exception {
        CommandResult result = new CommandRunner(Path.of(".."), temporary.resolve("home"),
                Duration.ofSeconds(10), Map.of(
                        "PERSEFONIA_REDIS_USERNAME", username,
                        "PERSEFONIA_REDIS_KEY_PREFIX", prefix))
                .run("sh", SCRIPT.toString(), "--generate-only", secret.toString(), acl.toString());
        return new Result(result.status(), result.stdout() + result.stderr());
    }

    private record Result(int status, String output) {}
}
