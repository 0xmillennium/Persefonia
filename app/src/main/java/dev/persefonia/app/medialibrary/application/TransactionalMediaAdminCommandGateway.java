package dev.persefonia.app.medialibrary.application;

import dev.persefonia.medialibrary.application.admin.AdminUploadAssetCommand;
import dev.persefonia.medialibrary.application.admin.AdminUploadAssetResult;
import dev.persefonia.medialibrary.application.admin.AssetMetadataUpdateResult;
import dev.persefonia.medialibrary.application.admin.MediaAdminCommandGateway;
import dev.persefonia.medialibrary.application.admin.MediaAdminCommandService;
import dev.persefonia.medialibrary.application.admin.UpdateAssetMetadataCommand;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnBean(MediaAdminCommandService.class)
public class TransactionalMediaAdminCommandGateway implements MediaAdminCommandGateway {
    private final MediaAdminCommandService service;

    public TransactionalMediaAdminCommandGateway(MediaAdminCommandService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    @Transactional
    public AdminUploadAssetResult upload(AdminUploadAssetCommand command) {
        return service.upload(command);
    }

    @Override
    @Transactional
    public AssetMetadataUpdateResult updateMetadata(UpdateAssetMetadataCommand command) {
        return service.updateMetadata(command);
    }
}
