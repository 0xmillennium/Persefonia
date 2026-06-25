package dev.persefonia.webpublic.contact;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.web.csrf.CsrfToken;

public record ContactPageViewModel(
        String title,
        String description,
        String canonicalUrl,
        List<String> stylesheetPaths,
        ContactForm form,
        Map<String, String> fieldErrors,
        String message,
        boolean submitted,
        CsrfToken csrfToken) {
    public ContactPageViewModel {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(canonicalUrl, "canonicalUrl must not be null");
        stylesheetPaths = List.copyOf(Objects.requireNonNull(stylesheetPaths, "stylesheetPaths must not be null"));
        form = Objects.requireNonNull(form, "form must not be null");
        fieldErrors = Map.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors must not be null"));
        Objects.requireNonNull(csrfToken, "csrfToken must not be null");
    }

    public boolean hasErrors() {
        return !fieldErrors.isEmpty();
    }

    public boolean hasMessage() {
        return message != null && !message.isBlank();
    }

    public boolean hasFieldError(String field) {
        return fieldErrors.containsKey(field);
    }

    public String fieldError(String field) {
        return fieldErrors.get(field);
    }
}
