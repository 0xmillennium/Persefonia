package dev.persefonia.webpublic.feed;

import java.nio.charset.StandardCharsets;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the public Atom 1.0 feed at the exact {@code /feed.xml} route.
 *
 * <p>The feed is anonymous, summary-only, and rendered by {@link PublicAtomFeedDocumentService}. No
 * repository or JDBC access happens here; entries come exclusively from the Discovery feed query
 * contract. The response carries an explicit public cache profile without {@code immutable}.
 */
@Controller
public final class PublicFeedController {
    static final String CACHE_CONTROL = PublicContentResponseHeaders.PUBLIC_MUTABLE_CACHE_CONTROL;
    private static final MediaType ATOM_UTF8 =
            new MediaType("application", "atom+xml", StandardCharsets.UTF_8);

    private final PublicAtomFeedDocumentService documents;

    public PublicFeedController(PublicAtomFeedDocumentService documents) {
        this.documents = Objects.requireNonNull(documents, "documents");
    }

    @GetMapping("/feed.xml")
    public ResponseEntity<String> feed() {
        return ResponseEntity.ok()
                .contentType(ATOM_UTF8)
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", CACHE_CONTROL)
                .body(documents.renderXml());
    }
}
