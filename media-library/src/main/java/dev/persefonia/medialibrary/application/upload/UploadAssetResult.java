package dev.persefonia.medialibrary.application.upload;

import dev.persefonia.medialibrary.domain.asset.AssetId;
import java.util.List;
import java.util.Objects;

public sealed interface UploadAssetResult {
    record Created(AssetId assetId) implements UploadAssetResult {
        public Created {
            Objects.requireNonNull(assetId, "assetId");
        }
    }

    record Duplicate(AssetId existingAssetId) implements UploadAssetResult {
        public Duplicate {
            Objects.requireNonNull(existingAssetId, "existingAssetId");
        }
    }

    record Rejected(List<UploadValidationError> errors) implements UploadAssetResult {
        public Rejected {
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
            if (errors.isEmpty()) {
                throw new IllegalArgumentException("errors must not be empty");
            }
        }
    }
}
