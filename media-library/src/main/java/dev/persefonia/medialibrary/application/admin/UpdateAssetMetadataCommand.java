package dev.persefonia.medialibrary.application.admin;

import dev.persefonia.medialibrary.application.authorization.MediaCommandActor;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import java.util.Objects;

public record UpdateAssetMetadataCommand(
        MediaCommandActor actor,
        AssetId assetId,
        AssetVisibility requestedVisibility,
        String altText,
        boolean decorative) {
    public UpdateAssetMetadataCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(requestedVisibility, "requestedVisibility");
    }
}
