package dev.persefonia.app.medialibrary.application;

import dev.persefonia.app.audit.integration.MediaAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
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

    public TransactionalMediaAdminCommandGateway(
            MediaAdminCommandService service,
            AppendAuditRecordPort audit,
            MediaAuditMapper auditMapper) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
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
        }
        return result;
    }
}
