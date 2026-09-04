package dev.persefonia.medialibrary.application.publicview;

import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.VariantName;
import java.util.Objects;

public final class AssetPublicVariantRouteFactory {
    public String route(AssetId assetId, VariantName variantName) {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(variantName, "variantName");
        return "/media/assets/" + assetId.value() + "/variants/" + variantName.databaseValue();
    }
}
