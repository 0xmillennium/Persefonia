package dev.persefonia.medialibrary.application.asset;

import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.Checksum;
import java.util.Optional;

public interface AssetRepository {
    Asset save(Asset asset);

    Optional<Asset> findById(AssetId id);

    Optional<Asset> findByChecksum(Checksum checksum);
}
