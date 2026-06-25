package dev.persefonia.webpublic.robots;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public final class PublicRobotsController {
    static final String CACHE_CONTROL = "public, max-age=3600, must-revalidate";
    private static final MediaType TEXT_UTF8 =
            new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final PublicRobotsDocumentService documents;

    public PublicRobotsController(PublicRobotsDocumentService documents) {
        this.documents = Objects.requireNonNull(documents, "documents");
    }

    @GetMapping("/robots.txt")
    public ResponseEntity<String> robots() {
        return ResponseEntity.ok()
                .contentType(TEXT_UTF8)
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", CACHE_CONTROL)
                .body(documents.render());
    }
}
