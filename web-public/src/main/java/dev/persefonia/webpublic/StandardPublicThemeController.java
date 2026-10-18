package dev.persefonia.webpublic;

import dev.persefonia.profileportfolio.application.service.StandardPublicThemeQueryService;
import dev.persefonia.profileportfolio.application.query.StandardPublicThemeView;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class StandardPublicThemeController {
    private static final String LIGHT = """
            color-scheme:light;--page-bg:#f7f9f8;--surface:#fff;--text:#1f2933;
            --muted:#5f6c72;--rule:#d7ddd7;--accent:#0f766e;--accent-strong:#0b4f4a;
            --code-bg:#17212b;--code-text:#edf2f7;
            """;
    private static final String DARK = """
            color-scheme:dark;--page-bg:#10161b;--surface:#1b252b;--text:#e9efed;
            --muted:#b6c4c3;--rule:#39474d;--accent:#7cd0c5;--accent-strong:#9de3d9;
            --code-bg:#090f14;--code-text:#edf2f7;
            """;

    private final StandardPublicThemeQueryService themes;
    private final PublicContentResponseHeaders headers;

    public StandardPublicThemeController(StandardPublicThemeQueryService themes, PublicContentResponseHeaders headers) {
        this.themes = Objects.requireNonNull(themes, "themes");
        this.headers = Objects.requireNonNull(headers, "headers");
    }

    @GetMapping(value = "/theme.css", produces = "text/css")
    public String stylesheet(HttpServletResponse response) {
        headers.applyPublicContentHeaders(response);
        StandardPublicThemeView preference = themes.current();
        return switch (preference) {
            case LIGHT -> ":root{" + LIGHT + "}";
            case DARK -> ":root{" + DARK + "}";
            case SYSTEM -> ":root{" + LIGHT + "}@media (prefers-color-scheme:dark){:root{" + DARK + "}}";
        };
    }
}
