package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DeploymentPreflightSecretAclContractTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsExactlyThePermittedAclMatrixWithRealOperatorOwnership() throws Exception {
        var fixture = PreflightTestSupport.runtimeFixture(temporaryDirectory);
        var result = fixture.run();

        if (Files.isDirectory(Path.of("/var/lib/persefonia/media"))) {
            assertThat(result.status()).isZero();
            assertThat(result.output()).isEqualTo("preflight: OK\n");
            assertThat(result.dockerCalls()).contains("network inspect backnet", "network inspect frontnet", "config --quiet");
        } else {
            // The fixed host media path is deliberately not created or overridden by this fixture.
            assertThat(result.status()).isNotZero();
            assertThat(result.output()).isEqualTo(
                    "preflight: durable media directory does not exist: /var/lib/persefonia/media\n");
            assertThat(result.dockerCalls()).isEqualTo("compose version --short\n");
        }
        assertThat(result.output()).doesNotContain(PreflightTestSupport.RuntimeFixture.SECRET_VALUE);
    }

    @ParameterizedTest
    @EnumSource(InvalidSecretLayout.class)
    void rejectsInvalidSecretLayoutBeforeNetworksOrComposeRendering(InvalidSecretLayout invalid) throws Exception {
        var fixture = PreflightTestSupport.runtimeFixture(temporaryDirectory);
        Path secret = fixture.secret("postgres_password");
        String acl = Files.readString(fixture.acls.resolve("postgres_password"));
        String expectedMessage = "secret ACL does not match";
        String expectedPath = secret.toString();

        switch (invalid) {
            case DIRECTORY_SYMLINK -> {
                Path target = fixture.stack.resolve("secrets-original");
                Files.move(fixture.secrets(), target);
                Files.createSymbolicLink(fixture.secrets(), target);
                expectedMessage = "secrets directory must not be a symlink";
            }
            case DIRECTORY_MODE -> {
                Files.setPosixFilePermissions(fixture.secrets(), PosixFilePermissions.fromString("rwxr-x---"));
                expectedMessage = "secrets directory mode must be 0700";
            }
            case DIRECTORY_OWNER -> {
                fixture.wrongOwnership(fixture.secrets(), "%u");
                expectedMessage = "owned by deployment operator UID";
            }
            case DIRECTORY_GROUP -> {
                fixture.wrongOwnership(fixture.secrets(), "%g");
                expectedMessage = "deployment operator primary GID";
            }
            case DIRECTORY_DEFAULT_ACL -> fixture.acl("secrets",
                    PreflightTestSupport.RuntimeFixture.DIRECTORY_ACL + "default:user::rwx\n");
            case DIRECTORY_NAMED_USER -> fixture.acl("secrets",
                    PreflightTestSupport.RuntimeFixture.DIRECTORY_ACL + "user:10001:r-x\n");
            case DIRECTORY_NAMED_GROUP -> fixture.acl("secrets",
                    PreflightTestSupport.RuntimeFixture.DIRECTORY_ACL + "group:70:r-x\n");
            case DIRECTORY_MASK -> fixture.acl("secrets",
                    PreflightTestSupport.RuntimeFixture.DIRECTORY_ACL + "mask::rwx\n");
            case SECRET_SYMLINK -> {
                Path target = fixture.stack.resolve("secret-original");
                Files.move(secret, target);
                Files.createSymbolicLink(secret, target);
                expectedMessage = "secret must not be a symlink";
            }
            case SECRET_NOT_REGULAR -> {
                Files.delete(secret);
                Files.createDirectory(secret);
                expectedMessage = "secret must be a regular file";
            }
            case SECRET_EMPTY -> {
                Files.writeString(secret, "");
                expectedMessage = "secret must be nonempty";
            }
            case SECRET_OWNER -> {
                fixture.wrongOwnership(secret, "%u");
                expectedMessage = "owned by deployment operator UID";
            }
            case SECRET_GROUP -> {
                fixture.wrongOwnership(secret, "%g");
                expectedMessage = "deployment operator primary GID";
            }
            case GROUP_READ -> fixture.acl("postgres_password", acl.replace("group::---", "group::r--"));
            case OTHER_READ -> fixture.acl("postgres_password", acl.replace("other::---", "other::r--"));
            case WRONG_MASK -> fixture.acl("postgres_password", acl.replace("mask::r--", "mask::rw-"));
            case MISSING_MASK -> fixture.acl("postgres_password", acl.replace("mask::r--\n", ""));
            case REQUIRED_UID_MISSING -> fixture.acl("postgres_password", acl.replace("user:70:r--\n", ""));
            case APP_UID_MISSING -> fixture.acl("postgres_password", acl.replace("user:10001:r--\n", ""));
            case REDIS_UID_MISSING -> {
                fixture.acl("redis_password", Files.readString(fixture.acls.resolve("redis_password"))
                        .replace("user:999:r--\n", ""));
                expectedPath = fixture.secret("redis_password").toString();
            }
            case WRONG_UID -> fixture.acl("postgres_password", acl.replace("user:70:", "user:71:"));
            case REQUIRED_UID_WRITE -> fixture.acl("postgres_password", acl.replace("user:70:r--", "user:70:rw-"));
            case REQUIRED_UID_EXECUTE -> fixture.acl("postgres_password", acl.replace("user:70:r--", "user:70:r-x"));
            case UNEXPECTED_USER -> fixture.acl("postgres_password", acl + "user:12345:r--\n");
            case NAMED_GROUP -> fixture.acl("postgres_password", acl + "group:70:r--\n");
            case FILE_DEFAULT_ACL -> fixture.acl("postgres_password", acl + "default:user::rw-\n");
            case MISSING_GETFACL -> {
                Files.delete(fixture.bin.resolve("getfacl"));
                expectedMessage = "required command not found: getfacl";
                expectedPath = "getfacl";
            }
        }
        if (invalid.name().startsWith("DIRECTORY_")) expectedPath = fixture.secrets().toString();

        var result = fixture.run();

        assertThat(result.status()).isNotZero();
        assertThat(result.output()).contains(expectedMessage, expectedPath)
                .doesNotContain(PreflightTestSupport.RuntimeFixture.SECRET_VALUE, "durable media", "configuration validation");
        assertThat(result.dockerCalls()).isEqualTo("compose version --short\n");
    }

    enum InvalidSecretLayout {
        DIRECTORY_SYMLINK, DIRECTORY_MODE, DIRECTORY_OWNER, DIRECTORY_GROUP, DIRECTORY_DEFAULT_ACL,
        DIRECTORY_NAMED_USER, DIRECTORY_NAMED_GROUP, DIRECTORY_MASK,
        SECRET_SYMLINK, SECRET_NOT_REGULAR, SECRET_EMPTY, SECRET_OWNER, SECRET_GROUP,
        GROUP_READ, OTHER_READ, WRONG_MASK, MISSING_MASK, REQUIRED_UID_MISSING, APP_UID_MISSING,
        REDIS_UID_MISSING, WRONG_UID, REQUIRED_UID_WRITE, REQUIRED_UID_EXECUTE,
        UNEXPECTED_USER, NAMED_GROUP, FILE_DEFAULT_ACL, MISSING_GETFACL
    }
}
