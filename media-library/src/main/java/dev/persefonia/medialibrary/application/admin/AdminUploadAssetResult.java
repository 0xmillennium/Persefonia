package dev.persefonia.medialibrary.application.admin;

import dev.persefonia.medialibrary.application.upload.UploadValidationError;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface AdminUploadAssetResult {
    record Created(AssetId assetId, ProcessingStatus processingStatus, String warning)
            implements AdminUploadAssetResult {
        public Created {
            Objects.requireNonNull(assetId, "assetId");
            Objects.requireNonNull(processingStatus, "processingStatus");
        }

        public Optional<String> warningOptional() {
            return Optional.ofNullable(warning);
        }
    }

    record Duplicate(AssetId existingAssetId) implements AdminUploadAssetResult {
        public Duplicate {
            Objects.requireNonNull(existingAssetId, "existingAssetId");
        }
    }

    record Rejected(List<UploadValidationError> errors) implements AdminUploadAssetResult {
        public Rejected {
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
            if (errors.isEmpty()) {
                throw new IllegalArgumentException("errors must not be empty");
            }
        }
    }
}
