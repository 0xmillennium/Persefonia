package dev.persefonia.automation.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InvocationLogTest {
    @TempDir Path temporary;

    @Test
    void preservesSpacesNewlinesAndEmptyArgumentsAcrossCalls() throws Exception {
        Path log = temporary.resolve("calls");
        Files.writeString(log, "3\0first value\0line one\nline two\0\0" + "1\0last\0");
        assertThat(InvocationLog.read(log)).containsExactly(
                List.of("first value", "line one\nline two", ""), List.of("last"));
    }

    @Test
    void rejectsIncompleteOrMalformedRecords() throws Exception {
        Path log = temporary.resolve("calls");
        for (String invalid : List.of("1\0missing", "not-a-count\0value\0", "2\0only-one\0")) {
            Files.writeString(log, invalid);
            assertThatThrownBy(() -> InvocationLog.read(log)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
