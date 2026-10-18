package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetPort;
import dev.persefonia.profileportfolio.application.service.ActiveCvPublicDownloadService;
import dev.persefonia.profileportfolio.application.service.ActiveCvPublicQueryService;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileRepository;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ActiveCvPublicApplicationConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "persefonia.media", name = "storage-root")
    ActiveCvPublicQueryService activeCvPublicQueryService(
            ActiveCvProfileRepository activeCvProfiles,
            SitePresentationSettingsRepository settings,
            ActiveCvPublicAssetPort assets) {
        return new ActiveCvPublicQueryService(activeCvProfiles, settings, assets);
    }

    @Bean
    @ConditionalOnProperty(prefix = "persefonia.media", name = "storage-root")
    ActiveCvPublicDownloadService activeCvPublicDownloadService(
            ActiveCvProfileRepository activeCvProfiles,
            SitePresentationSettingsRepository settings,
            ActiveCvPublicAssetPort assets) {
        return new ActiveCvPublicDownloadService(activeCvProfiles, settings, assets);
    }
}
