package dev.persefonia.medialibrary.application.admin;

import java.util.Objects;
import java.util.Optional;

public record MediaAdminAssetVariantDetails(
        String name,
        int width,
        int height,
        String contentType,
        long sizeBytes,
        String checksum,
        String publicRoute) {
    public MediaAdminAssetVariantDetails {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(checksum, "checksum");
    }

    public Optional<String> publicRouteOptional() {
        return Optional.ofNullable(publicRoute);
    }
}
