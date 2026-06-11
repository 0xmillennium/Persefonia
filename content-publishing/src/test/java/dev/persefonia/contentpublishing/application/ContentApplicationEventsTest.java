package dev.persefonia.contentpublishing.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.event.ContentPublishingEvent;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContentApplicationEventsTest {
    @Test
    void eventContractContainsOnlySafeCommonMetadata() {
        Set<String> methods = Arrays.stream(ContentPublishingEvent.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(methods).containsExactlyInAnyOrder("contentId", "type", "language", "actor", "occurredAt");
        assertThat(methods).doesNotContain("markdownSource", "renderedHtml");
    }
}
