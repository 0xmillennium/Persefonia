package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.contentpublishing.application.event.ContentPublishingEvent;
import dev.persefonia.contentpublishing.application.port.ContentPublishingEventPublisher;

final class NoOpContentPublishingEventPublisher implements ContentPublishingEventPublisher {
    @Override
    public void publish(ContentPublishingEvent event) {
        // Durable downstream subscriber semantics are intentionally not wired yet.
    }
}
