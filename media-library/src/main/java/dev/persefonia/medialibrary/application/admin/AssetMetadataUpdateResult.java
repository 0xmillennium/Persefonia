package dev.persefonia.medialibrary.application.admin;

import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import java.util.List;
import java.util.Objects;

public sealed interface AssetMetadataUpdateResult {
    record Updated(
            AssetId assetId,
            AssetVisibility beforeVisibility,
            AssetVisibility afterVisibility,
            boolean visibilityChanged) implements AssetMetadataUpdateResult {
        public Updated(AssetId assetId) {
            this(assetId, AssetVisibility.PRIVATE, AssetVisibility.PRIVATE, false);
        }

        public Updated {
            Objects.requireNonNull(assetId, "assetId");
            Objects.requireNonNull(beforeVisibility, "beforeVisibility");
            Objects.requireNonNull(afterVisibility, "afterVisibility");
        }
    }

    record NotFound(AssetId assetId) implements AssetMetadataUpdateResult {
        public NotFound {
            Objects.requireNonNull(assetId, "assetId");
        }
    }

    record Rejected(List<MediaAdminCommandError> errors) implements AssetMetadataUpdateResult {
        public Rejected {
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
            if (errors.isEmpty()) {
                throw new IllegalArgumentException("errors must not be empty");
            }
        }
    }
}
