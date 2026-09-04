package dev.persefonia.medialibrary.application.publicview;

import dev.persefonia.medialibrary.domain.asset.AssetId;
import java.util.List;

public interface AssetPublicVariantRouteQuery {
    List<String> findStableVariantRoutes(AssetId assetId, int limit);
}
