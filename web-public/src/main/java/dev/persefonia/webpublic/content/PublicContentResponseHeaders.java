package dev.persefonia.webpublic.content;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

@Component
public final class PublicContentResponseHeaders {
    public static final String PUBLIC_MUTABLE_CACHE_CONTROL = "public, no-cache, must-revalidate";
    public static final String PUBLIC_NOT_FOUND_CACHE_CONTROL = "no-store, private";
    static final String PUBLIC_SEARCH_CACHE_CONTROL = "no-store, private";
    static final String PUBLIC_CONTACT_CACHE_CONTROL = "no-store, private";

    public void applyPublicContentHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", PUBLIC_MUTABLE_CACHE_CONTROL);
    }

    public void applyPublicRedirectHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", PUBLIC_MUTABLE_CACHE_CONTROL);
    }

    public void applyPublicNotFoundHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", PUBLIC_NOT_FOUND_CACHE_CONTROL);
    }

    public void applyPublicSearchHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", PUBLIC_SEARCH_CACHE_CONTROL);
    }

    public void applyPublicContactHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", PUBLIC_CONTACT_CACHE_CONTROL);
    }
}
