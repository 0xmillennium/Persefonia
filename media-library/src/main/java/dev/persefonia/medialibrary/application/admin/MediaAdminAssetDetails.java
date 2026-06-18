package dev.persefonia.medialibrary.application.admin;

import java.util.List;
import java.util.Objects;

public record MediaAdminAssetDetails(
        MediaAdminAssetListItem summary,
        String altText,
        boolean decorative,
        List<MediaAdminAssetVariantDetails> variants,
        List<MediaAdminAssetValidationResultDetails> validationResults) {
    public MediaAdminAssetDetails {
        Objects.requireNonNull(summary, "summary");
        variants = List.copyOf(Objects.requireNonNull(variants, "variants"));
        validationResults = List.copyOf(Objects.requireNonNull(validationResults, "validationResults"));
    }
}
