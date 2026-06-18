package dev.persefonia.webpublic.media;

import dev.persefonia.medialibrary.application.publicview.PublicImageVariantContentService;
import java.io.InputStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public final class PublicMediaAssetController {
    private static final String CACHE_CONTROL = "public, max-age=86400";

    private final ObjectProvider<PublicImageVariantContentService> contentServices;

    public PublicMediaAssetController(ObjectProvider<PublicImageVariantContentService> contentServices) {
        this.contentServices = contentServices;
    }

    @GetMapping("/media/assets/{assetId}/variants/{variantName}")
    public ResponseEntity<InputStreamResource> variant(
            @PathVariable("assetId") String assetId,
            @PathVariable("variantName") String variantName) {
        PublicImageVariantContentService contentService = contentServices.getIfAvailable();
        if (contentService == null) {
            return ResponseEntity.notFound().build();
        }
        return contentService.openVariant(assetId, variantName)
                .map(content -> response(
                        content.inputStream(),
                        content.contentType(),
                        content.contentLength()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<InputStreamResource> response(
            InputStream inputStream,
            String contentType,
            long contentLength) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(contentLength)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .body(new InputStreamResource(inputStream));
    }
}
