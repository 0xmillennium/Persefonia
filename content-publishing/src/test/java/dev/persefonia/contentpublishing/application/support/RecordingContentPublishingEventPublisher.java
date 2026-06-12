package dev.persefonia.contentpublishing.application.support;

import dev.persefonia.contentpublishing.application.event.ContentPublishingEvent;
import dev.persefonia.contentpublishing.application.port.ContentPublishingEventPublisher;
import java.util.ArrayList;
import java.util.List;

public final class RecordingContentPublishingEventPublisher implements ContentPublishingEventPublisher {
    private final List<ContentPublishingEvent> events = new ArrayList<>();

    @Override
    public void publish(ContentPublishingEvent event) {
        events.add(event);
    }

    public List<ContentPublishingEvent> events() {
        return List.copyOf(events);
    }

    public List<Class<?>> eventTypes() {
        return events.stream().<Class<?>>map(Object::getClass).toList();
    }
}
