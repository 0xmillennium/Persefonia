package dev.persefonia.contentpublishing.application.rendering;

public class MarkdownRenderingException extends RuntimeException {
    public MarkdownRenderingException(String message) {
        super(message);
    }

    public MarkdownRenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}
