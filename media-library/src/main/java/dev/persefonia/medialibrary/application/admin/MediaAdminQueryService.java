package dev.persefonia.medialibrary.application.admin;

import dev.persefonia.medialibrary.domain.asset.AssetId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MediaAdminQueryService {
    private final MediaAdminReadModel readModel;

    public MediaAdminQueryService(MediaAdminReadModel readModel) {
        this.readModel = Objects.requireNonNull(readModel, "readModel");
    }

    public List<MediaAdminAssetListItem> listAssets() {
        return readModel.listAssets();
    }

    public Optional<MediaAdminAssetDetails> findAssetDetails(AssetId id) {
        return readModel.findAssetDetails(id);
    }
}
