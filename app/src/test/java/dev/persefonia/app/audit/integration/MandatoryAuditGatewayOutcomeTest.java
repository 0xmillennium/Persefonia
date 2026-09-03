package dev.persefonia.app.audit.integration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.persefonia.app.communication.application.TransactionalContactMessageStatusCommandGateway;
import dev.persefonia.app.discovery.application.TransactionalAdminRedirectCommandGateway;
import dev.persefonia.app.identityaccess.bootstrap.TransactionalAdminBootstrapGateway;
import dev.persefonia.app.medialibrary.application.TransactionalMediaAdminCommandGateway;
import dev.persefonia.app.profileportfolio.application.TransactionalActiveCvCommandGateway;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusCommand;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusCommandService;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusResult;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.discovery.application.redirect.CreateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import dev.persefonia.discovery.application.service.AdminRedirectCommandService;
import dev.persefonia.discovery.domain.RedirectRuleId;
import dev.persefonia.identityaccess.application.admin.bootstrap.AdminBootstrapOutcome;
import dev.persefonia.identityaccess.application.admin.bootstrap.AdminBootstrapResult;
import dev.persefonia.identityaccess.application.admin.bootstrap.AdminBootstrapUseCase;
import dev.persefonia.identityaccess.domain.admin.AdminAccount;
import dev.persefonia.identityaccess.domain.admin.access.AdminIdentityClaims;
import dev.persefonia.medialibrary.application.admin.AdminUploadAssetCommand;
import dev.persefonia.medialibrary.application.admin.AdminUploadAssetResult;
import dev.persefonia.medialibrary.application.admin.AssetMetadataUpdateResult;
import dev.persefonia.medialibrary.application.admin.MediaAdminCommandService;
import dev.persefonia.medialibrary.application.admin.UpdateAssetMetadataCommand;
import dev.persefonia.medialibrary.application.upload.UploadValidationError;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.profileportfolio.application.command.ActiveCvUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpdateActiveCvCommand;
import dev.persefonia.profileportfolio.application.service.ActiveCvCommandService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MandatoryAuditGatewayOutcomeTest {
    @Test
    void redirectNoopRejectedAlreadyInactiveAndNotFoundDoNotAppend() {
        AppendAuditRecordPort audit = mock(AppendAuditRecordPort.class);
        AdminRedirectCommandService service = mock(AdminRedirectCommandService.class);
        var gateway = new TransactionalAdminRedirectCommandGateway(
                service, audit, mock(DiscoveryAuditMapper.class));
        CreateManualRedirectCommand create = mock(CreateManualRedirectCommand.class);
        DeactivateManualRedirectCommand deactivate = mock(DeactivateManualRedirectCommand.class);
        RedirectRuleId id = new RedirectRuleId(UUID.randomUUID());
        when(service.create(create)).thenReturn(
                mock(RedirectRuleCreationResult.Noop.class),
                new RedirectRuleCreationResult.Rejected(RedirectRuleCreationResult.Reason.INVALID_INPUT));
        when(service.deactivate(deactivate)).thenReturn(
                mock(DeactivateRedirectRuleResult.AlreadyInactive.class),
                new DeactivateRedirectRuleResult.NotFound(id));

        gateway.create(create);
        gateway.create(create);
        gateway.deactivate(deactivate);
        gateway.deactivate(deactivate);

        verify(audit, never()).append(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void contactNotFoundAndRejectedDoNotAppend() {
        AppendAuditRecordPort audit = mock(AppendAuditRecordPort.class);
        UpdateContactMessageStatusCommandService service = mock(UpdateContactMessageStatusCommandService.class);
        var gateway = new TransactionalContactMessageStatusCommandGateway(
                service, audit, mock(CommunicationAuditMapper.class));
        UpdateContactMessageStatusCommand command = mock(UpdateContactMessageStatusCommand.class);
        when(service.update(command)).thenReturn(
                new UpdateContactMessageStatusResult.NotFound(ContactMessageId.newId()),
                new UpdateContactMessageStatusResult.Rejected("rejected"));

        gateway.update(command);
        gateway.update(command);

        verify(audit, never()).append(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mediaDuplicateRejectedAndMetadataFailuresDoNotAppend() {
        AppendAuditRecordPort audit = mock(AppendAuditRecordPort.class);
        MediaAdminCommandService service = mock(MediaAdminCommandService.class);
        var gateway = new TransactionalMediaAdminCommandGateway(
                service, audit, mock(MediaAuditMapper.class));
        AdminUploadAssetCommand upload = mock(AdminUploadAssetCommand.class);
        UpdateAssetMetadataCommand update = mock(UpdateAssetMetadataCommand.class);
        when(service.upload(upload)).thenReturn(
                new AdminUploadAssetResult.Duplicate(AssetId.newId()),
                new AdminUploadAssetResult.Rejected(List.of(mock(UploadValidationError.class))));
        when(service.updateMetadata(update)).thenReturn(
                new AssetMetadataUpdateResult.NotFound(AssetId.newId()),
                mock(AssetMetadataUpdateResult.Rejected.class));

        gateway.upload(upload);
        gateway.upload(upload);
        gateway.updateMetadata(update);
        gateway.updateMetadata(update);

        verify(audit, never()).append(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void inactiveCvResultDoesNotAppend() {
        AppendAuditRecordPort audit = mock(AppendAuditRecordPort.class);
        ActiveCvCommandService service = mock(ActiveCvCommandService.class);
        var gateway = new TransactionalActiveCvCommandGateway(
                service, audit, mock(ProfilePortfolioAuditMapper.class));
        UpdateActiveCvCommand command = mock(UpdateActiveCvCommand.class);
        when(service.update(command)).thenReturn(ActiveCvUpdateResult.rejected(List.of()));

        gateway.update(command);

        verify(audit, never()).append(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void existingAdminLoginDoesNotAppend() {
        AppendAuditRecordPort audit = mock(AppendAuditRecordPort.class);
        AdminBootstrapUseCase useCase = mock(AdminBootstrapUseCase.class);
        var gateway = new TransactionalAdminBootstrapGateway(
                useCase, audit, mock(IdentityAccessAuditMapper.class));
        AdminIdentityClaims claims = mock(AdminIdentityClaims.class);
        when(useCase.resolveOrBootstrap(claims)).thenReturn(new AdminBootstrapResult(
                mock(AdminAccount.class), AdminBootstrapOutcome.EXISTING_ACCOUNT));

        gateway.resolveOrBootstrap(claims);

        verify(audit, never()).append(org.mockito.ArgumentMatchers.any());
    }
}
