package dev.persefonia.automation.deploy;

import dev.persefonia.automation.support.CommandRunner;
import dev.persefonia.automation.support.InvocationLog;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RcIngressSmokeContractTest {
    private static final Path SCRIPT = Path.of("../scripts/deploy/verify-rc-ingress.sh").toAbsolutePath();
    @TempDir Path temp;

    @Test
    void acceptsOnlyThePublicHttpsRoutesWithoutFollowingRedirects() throws Exception {
        Result result = run(Map.of());
        assertThat(result.status()).isZero();
        assertThat(result.calls()).contains("https://0xmillennium.dev/\n",
                "https://0xmillennium.dev/robots.txt\n", "https://0xmillennium.dev/admin\n",
                "--connect-timeout\n5\n", "--max-time\n15\n");
        assertThat(result.calls()).doesNotContain("--resolve", "--location", "-L\n", "http://", "127.0.0.1");
        assertThat(run(Map.of("INGRESS_LOCATION", "https://0xmillennium.dev/oauth2/authorization/authelia")).status())
                .isZero();
    }

    @Test
    void rejectsBadStatusRedirectCacheAndNetworkWithinSixAttempts() throws Exception {
        for (Map<String, String> failure : List.of(
                Map.of("INGRESS_HOME_STATUS", "503"), Map.of("INGRESS_ROBOTS_STATUS", "404"),
                Map.of("INGRESS_ADMIN_STATUS", "200"), Map.of("INGRESS_LOCATION", ""),
                Map.of("INGRESS_OMIT_LOCATION", "1"),
                Map.of("INGRESS_LOCATION", "/login"),
                Map.of("INGRESS_LOCATION", "https://evil.invalid/oauth2/authorization/authelia"),
                Map.of("INGRESS_LOCATION", "/oauth2/authorization/authelia;jsessionid=x"),
                Map.of("INGRESS_CACHE_CONTROL", "private"), Map.of("INGRESS_NETWORK_FAIL", "1"))) {
            Result result = run(failure);
            assertThat(result.status()).as(failure.toString()).isNotZero();
            assertThat(result.calls().split("https://0xmillennium.dev/\\n", -1)).hasSizeLessThanOrEqualTo(7);
        }
    }

    private Result run(Map<String, String> overrides) throws Exception {
        Path dir = Files.createTempDirectory(temp, "ingress-");
        Path bin = Files.createDirectory(dir.resolve("bin"));
        Path curl = bin.resolve("curl");
        Files.copy(Path.of("src/test/resources/fakes/fake-ingress-curl.sh"), curl);
        curl.toFile().setExecutable(true);
        Path sleep = bin.resolve("sleep");
        Files.writeString(sleep, "#!/usr/bin/env bash\nexit 0\n");
        sleep.toFile().setExecutable(true);
        Path log = dir.resolve("calls");
        ProcessBuilder builder = new ProcessBuilder(SCRIPT.toString());
        builder.environment().put("PATH", bin + ":" + builder.environment().get("PATH"));
        builder.environment().put("INGRESS_CURL_LOG", log.toString());
        builder.environment().putAll(overrides);
        var result = CommandRunner.execute(builder);
        return new Result(result.status(), result.stdout(), InvocationLog.argumentsAsLines(log));
    }

    private record Result(int status, String stdout, String calls) {}
}
