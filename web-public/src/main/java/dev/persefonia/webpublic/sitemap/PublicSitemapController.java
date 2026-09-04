package dev.persefonia.webpublic.sitemap;

import java.nio.charset.StandardCharsets;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public final class PublicSitemapController {
    static final String CACHE_CONTROL = PublicContentResponseHeaders.PUBLIC_MUTABLE_CACHE_CONTROL;
    private static final MediaType XML_UTF8 =
            new MediaType("application", "xml", StandardCharsets.UTF_8);

    private final PublicSitemapDocumentService documents;

    public PublicSitemapController(PublicSitemapDocumentService documents) {
        this.documents = Objects.requireNonNull(documents, "documents");
    }

    @GetMapping("/sitemap.xml")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok()
                .contentType(XML_UTF8)
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", CACHE_CONTROL)
                .body(documents.renderXml());
    }
}
