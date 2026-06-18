package dev.persefonia.medialibrary.application.processing;

import dev.persefonia.medialibrary.domain.asset.AssetId;

public sealed interface ProcessImageAssetResult {
    record Processed(AssetId assetId) implements ProcessImageAssetResult {}

    record Failed(AssetId assetId, String reason) implements ProcessImageAssetResult {}

    record AlreadyProcessed(AssetId assetId) implements ProcessImageAssetResult {}

    record NotProcessable(AssetId assetId) implements ProcessImageAssetResult {}

    record NotFound(AssetId assetId) implements ProcessImageAssetResult {}
}
