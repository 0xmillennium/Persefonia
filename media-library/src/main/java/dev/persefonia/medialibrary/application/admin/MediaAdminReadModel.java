package dev.persefonia.medialibrary.application.admin;

import dev.persefonia.medialibrary.domain.asset.AssetId;
import java.util.List;
import java.util.Optional;

public interface MediaAdminReadModel {
    List<MediaAdminAssetListItem> listAssets();

    Optional<MediaAdminAssetDetails> findAssetDetails(AssetId id);
}
