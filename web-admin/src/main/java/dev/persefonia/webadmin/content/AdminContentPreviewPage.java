package dev.persefonia.webadmin.content;

import java.util.List;
import java.util.Objects;

public record AdminContentPreviewPage(
        AdminContentPageChrome chrome,
        String heading,
        String editLink,
        String sanitizedHtml,
        boolean containsMermaid,
        List<String> globalErrors) {
    public AdminContentPreviewPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(editLink, "editLink");
        globalErrors = List.copyOf(Objects.requireNonNull(globalErrors, "globalErrors"));
    }

    public boolean available() {
        return sanitizedHtml != null;
    }
}
