package dev.persefonia.medialibrary.application.admin;

public interface MediaAdminCommandGateway {
    AdminUploadAssetResult upload(AdminUploadAssetCommand command);

    AssetMetadataUpdateResult updateMetadata(UpdateAssetMetadataCommand command);
}
