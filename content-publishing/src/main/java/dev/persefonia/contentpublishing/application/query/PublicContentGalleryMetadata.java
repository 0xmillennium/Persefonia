package dev.persefonia.contentpublishing.application.query;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PublicContentGalleryMetadata(Optional<String> coverAssetId, List<String> tags) {
    public PublicContentGalleryMetadata {
        Objects.requireNonNull(coverAssetId, "coverAssetId");
        tags = List.copyOf(tags);
    }
}
