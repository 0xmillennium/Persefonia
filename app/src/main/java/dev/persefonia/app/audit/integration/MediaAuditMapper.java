package dev.persefonia.app.audit.integration;

import static dev.persefonia.app.audit.integration.AdminAuditCommandFactory.metadata;

import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.medialibrary.application.admin.AdminUploadAssetCommand;
import dev.persefonia.medialibrary.application.admin.AdminUploadAssetResult;
import dev.persefonia.medialibrary.application.admin.AssetMetadataUpdateResult;
import dev.persefonia.medialibrary.application.admin.UpdateAssetMetadataCommand;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class MediaAuditMapper {
    private final AdminAuditCommandFactory factory;

    public MediaAuditMapper(AdminAuditCommandFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public AppendAuditRecordCommand uploaded(
            AdminUploadAssetCommand command, AdminUploadAssetResult.Created result) {
        return factory.admin(
                AuditActionCatalog.ASSET_UPLOADED,
                command.actor().identityRef(),
                AuditEntityCatalog.ASSET,
                result.assetId().value(),
                List.of(),
                List.of(metadata("processing_status", result.processingStatus())));
    }

    public AppendAuditRecordCommand metadataUpdated(
            UpdateAssetMetadataCommand command, AssetMetadataUpdateResult.Updated result) {
        return factory.admin(
                AuditActionCatalog.ASSET_METADATA_UPDATED,
                command.actor().identityRef(),
                AuditEntityCatalog.ASSET,
                result.assetId().value(),
                List.of(),
                List.of(
                        metadata("visibility", command.requestedVisibility()),
                        metadata("decorative", command.decorative())));
    }
}
