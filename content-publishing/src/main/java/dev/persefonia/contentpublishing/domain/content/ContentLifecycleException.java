package dev.persefonia.contentpublishing.domain.content;

public class ContentLifecycleException extends IllegalStateException {
    public ContentLifecycleException(String message) {
        super(message);
    }
}
