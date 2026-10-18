package dev.persefonia.app.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.persefonia.app.medialibrary.application.MediaLibraryActiveCvPublicAssetAdapter;
import dev.persefonia.app.medialibrary.application.MediaLibraryAdminApplicationConfiguration;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.medialibrary.application.admin.MediaAdminQueryService;
import dev.persefonia.medialibrary.application.admin.MediaAdminReadModel;
import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.authorization.MediaCommandAuthorizationPolicy;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetContentService;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetQueryService;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetReadModel;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetPort;
import dev.persefonia.profileportfolio.application.service.ActiveCvPublicDownloadService;
import dev.persefonia.profileportfolio.application.service.ActiveCvPublicQueryService;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileRepository;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ActiveCvPublicActivationContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AdminCommandAuthorizationPolicy.class, AdminCommandAuthorizationPolicy::new)
            .withBean(MediaAdminReadModel.class, () -> mock(MediaAdminReadModel.class))
            .withBean(PublicPdfAssetReadModel.class, () -> mock(PublicPdfAssetReadModel.class))
            .withBean(AssetRepository.class, () -> mock(AssetRepository.class))
            .withBean(AssetStoragePort.class, () -> mock(AssetStoragePort.class))
            .withBean(ActiveCvProfileRepository.class, () -> mock(ActiveCvProfileRepository.class))
            .withBean(SitePresentationSettingsRepository.class, () -> mock(SitePresentationSettingsRepository.class));

    @TempDir
    Path storageRoot;

    @Test
    void storageDependentPublicCvCapabilityIsAbsentWithoutStorageRoot() {
        contextRunner.withUserConfiguration(MediaLibraryAdminApplicationConfiguration.class,
                MediaLibraryActiveCvPublicAssetAdapter.class, ActiveCvPublicApplicationConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MediaCommandAuthorizationPolicy.class);
            assertThat(context).hasSingleBean(MediaAdminQueryService.class);
            assertThat(context).hasSingleBean(PublicPdfAssetQueryService.class);
            assertThat(context).doesNotHaveBean(PublicPdfAssetContentService.class);
            assertThat(context).doesNotHaveBean(MediaLibraryActiveCvPublicAssetAdapter.class);
            assertThat(context).doesNotHaveBean(ActiveCvPublicAssetPort.class);
            assertThat(context).doesNotHaveBean(ActiveCvPublicQueryService.class);
            assertThat(context).doesNotHaveBean(ActiveCvPublicDownloadService.class);
        });
    }

    @Test
    void configuredStorageActivatesMediaBackedPublicCvCapability() {
        assertConfiguredStorageActivates(contextRunner.withUserConfiguration(MediaLibraryAdminApplicationConfiguration.class,
                MediaLibraryActiveCvPublicAssetAdapter.class, ActiveCvPublicApplicationConfiguration.class));
        assertConfiguredStorageActivates(contextRunner.withUserConfiguration(ActiveCvPublicApplicationConfiguration.class,
                MediaLibraryActiveCvPublicAssetAdapter.class, MediaLibraryAdminApplicationConfiguration.class));
    }

    private void assertConfiguredStorageActivates(ApplicationContextRunner runner) {
        runner.withPropertyValues("persefonia.media.storage-root=" + storageRoot).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PublicPdfAssetContentService.class);
            assertThat(context).hasSingleBean(MediaLibraryActiveCvPublicAssetAdapter.class);
            assertThat(context).hasSingleBean(ActiveCvPublicAssetPort.class);
            assertThat(context).hasSingleBean(ActiveCvPublicQueryService.class);
            assertThat(context).hasSingleBean(ActiveCvPublicDownloadService.class);
        });
    }
}
