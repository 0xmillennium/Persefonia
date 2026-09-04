package dev.persefonia.webpublic.media;

import dev.persefonia.medialibrary.application.publicview.PublicImageVariantContentService;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
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
            return missing();
        }
        return contentService.openVariant(assetId, variantName)
                .map(content -> response(
                        content.inputStream(),
                        content.contentType(),
                        content.contentLength()))
                .orElseGet(PublicMediaAssetController::missing);
    }

    private static ResponseEntity<InputStreamResource> response(
            InputStream inputStream,
            String contentType,
            long contentLength) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(contentLength)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, PublicContentResponseHeaders.PUBLIC_MUTABLE_CACHE_CONTROL)
                .body(new InputStreamResource(inputStream));
    }

    private static ResponseEntity<InputStreamResource> missing() {
        return ResponseEntity.notFound()
                .header(HttpHeaders.CACHE_CONTROL, PublicContentResponseHeaders.PUBLIC_NOT_FOUND_CACHE_CONTROL)
                .build();
    }
}
