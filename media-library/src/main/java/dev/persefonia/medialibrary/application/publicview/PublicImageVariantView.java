package dev.persefonia.medialibrary.application.publicview;

public record PublicImageVariantView(
        String assetId,
        String variantName,
        String url,
        int width,
        int height,
        String contentType,
        long contentLength,
        boolean decorative,
        String altTextForRendering) {
}
