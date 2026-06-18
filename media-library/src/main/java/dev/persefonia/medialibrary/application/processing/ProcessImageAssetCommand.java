package dev.persefonia.medialibrary.application.processing;

import dev.persefonia.medialibrary.domain.asset.AssetId;
import java.util.Objects;

public record ProcessImageAssetCommand(AssetId assetId) {
    public ProcessImageAssetCommand {
        Objects.requireNonNull(assetId, "assetId");
    }
}
