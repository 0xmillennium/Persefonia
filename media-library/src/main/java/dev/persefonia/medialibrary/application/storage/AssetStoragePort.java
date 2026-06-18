package dev.persefonia.medialibrary.application.storage;

import java.io.InputStream;
import dev.persefonia.medialibrary.domain.asset.StoragePath;

public interface AssetStoragePort {
    StagedAssetObject stageOriginal(OriginalAssetStagingRequest request);

    InputStream openStaged(StagedAssetObject stagedObject);

    StoredAssetObject commitStaged(StagedAssetObject stagedObject, FinalAssetStorageKey finalKey);

    InputStream openStored(StoragePath storagePath);

    StoredAssetObject storeVariant(VariantStorageRequest request);

    void deleteStagedIfExists(StagedAssetObject stagedObject);

    void deleteStoredIfExists(StoredAssetObject storedObject);

    void deleteStoredByPathIfExists(StoragePath storagePath);
}
