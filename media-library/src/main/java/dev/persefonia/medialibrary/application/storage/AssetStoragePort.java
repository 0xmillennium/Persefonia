package dev.persefonia.medialibrary.application.storage;

import java.io.InputStream;

public interface AssetStoragePort {
    StagedAssetObject stageOriginal(OriginalAssetStagingRequest request);

    InputStream openStaged(StagedAssetObject stagedObject);

    StoredAssetObject commitStaged(StagedAssetObject stagedObject, FinalAssetStorageKey finalKey);

    void deleteStagedIfExists(StagedAssetObject stagedObject);

    void deleteStoredIfExists(StoredAssetObject storedObject);
}
