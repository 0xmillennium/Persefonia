package dev.persefonia.automation.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeyValueProtocolTest {
    private final List<String> keys = List.of("format_version", "source_sha");

    @Test
    void rejectsMissingDuplicateUnknownOutOfOrderAndNonLfRecords() {
        for (String data : List.of(
                "format_version=1\n",
                "format_version=1\nformat_version=2\n",
                "format_version=1\nunknown=value\n",
                "source_sha=value\nformat_version=1\n",
                "format_version=1\nsource_sha=value",
                "format_version=1\r\nsource_sha=value\n",
                "format_version=1\nsource_sha=va\0lue\n")) {
            assertThatThrownBy(() -> KeyValueProtocol.parse(data, keys)).as(data)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
