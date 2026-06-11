package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.application.event.ContentPublishingEvent;

public interface ContentPublishingEventPublisher {
    void publish(ContentPublishingEvent event);
}
