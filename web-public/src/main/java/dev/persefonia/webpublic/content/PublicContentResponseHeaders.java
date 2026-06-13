package dev.persefonia.webpublic.content;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

@Component
public final class PublicContentResponseHeaders {
    static final String PUBLIC_CONTENT_CACHE_CONTROL = "public, max-age=60";
    static final String PUBLIC_NOT_FOUND_CACHE_CONTROL = "no-store, private";

    public void applyPublicContentHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", PUBLIC_CONTENT_CACHE_CONTROL);
    }

    public void applyPublicNotFoundHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", PUBLIC_NOT_FOUND_CACHE_CONTROL);
    }
}
