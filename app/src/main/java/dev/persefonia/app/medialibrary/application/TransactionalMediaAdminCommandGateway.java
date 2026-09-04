package dev.persefonia.app.medialibrary.application;

import dev.persefonia.app.audit.integration.MediaAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationRegistrar;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationSignal;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.application.admin.AdminUploadAssetCommand;
import dev.persefonia.medialibrary.application.admin.AdminUploadAssetResult;
import dev.persefonia.medialibrary.application.admin.AssetMetadataUpdateResult;
import dev.persefonia.medialibrary.application.admin.MediaAdminCommandGateway;
import dev.persefonia.medialibrary.application.admin.MediaAdminCommandService;
import dev.persefonia.medialibrary.application.admin.UpdateAssetMetadataCommand;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalMediaAdminCommandGateway implements MediaAdminCommandGateway {
    private final MediaAdminCommandService service;
    private final AppendAuditRecordPort audit;
    private final MediaAuditMapper auditMapper;
    private final PublicCacheInvalidationRegistrar cacheInvalidation;

    public TransactionalMediaAdminCommandGateway(
            MediaAdminCommandService service,
            AppendAuditRecordPort audit,
            MediaAuditMapper auditMapper) {
        this(service, audit, auditMapper, PublicCacheInvalidationRegistrar.noOp());
    }

    public TransactionalMediaAdminCommandGateway(
            MediaAdminCommandService service,
            AppendAuditRecordPort audit,
            MediaAuditMapper auditMapper,
            PublicCacheInvalidationRegistrar cacheInvalidation) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
        this.cacheInvalidation = Objects.requireNonNull(cacheInvalidation, "cacheInvalidation");
    }

    @Override
    @Transactional
    public AdminUploadAssetResult upload(AdminUploadAssetCommand command) {
        AdminUploadAssetResult result = service.upload(command);
        if (result instanceof AdminUploadAssetResult.Created created) {
            audit.append(auditMapper.uploaded(command, created));
        }
        return result;
    }

    @Override
    @Transactional
    public AssetMetadataUpdateResult updateMetadata(UpdateAssetMetadataCommand command) {
        AssetMetadataUpdateResult result = service.updateMetadata(command);
        if (result instanceof AssetMetadataUpdateResult.Updated updated) {
            audit.append(auditMapper.metadataUpdated(command, updated));
            if (updated.visibilityChanged()
                    && (updated.beforeVisibility() == AssetVisibility.PUBLIC
                    || updated.afterVisibility() == AssetVisibility.PUBLIC)) {
                cacheInvalidation.register(new PublicCacheInvalidationSignal.AssetVisibilityChanged(
                        updated.assetId(), updated.beforeVisibility(), updated.afterVisibility()));
            }
        }
        return result;
    }
}
