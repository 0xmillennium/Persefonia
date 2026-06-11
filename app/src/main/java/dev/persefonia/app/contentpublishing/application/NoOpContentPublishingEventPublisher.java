package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.contentpublishing.application.event.ContentPublishingEvent;
import dev.persefonia.contentpublishing.application.port.ContentPublishingEventPublisher;

final class NoOpContentPublishingEventPublisher implements ContentPublishingEventPublisher {
    @Override
    public void publish(ContentPublishingEvent event) {
        // Downstream event listeners are intentionally deferred beyond Milestone 3.
    }
}
