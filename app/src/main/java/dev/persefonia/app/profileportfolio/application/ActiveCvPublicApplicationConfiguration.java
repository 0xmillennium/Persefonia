package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.profileportfolio.application.port.ActiveCvPublicAssetPort;
import dev.persefonia.profileportfolio.application.service.ActiveCvPublicDownloadService;
import dev.persefonia.profileportfolio.application.service.ActiveCvPublicQueryService;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileRepository;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ActiveCvPublicApplicationConfiguration {
    @Bean
    @ConditionalOnBean(ActiveCvPublicAssetPort.class)
    ActiveCvPublicQueryService activeCvPublicQueryService(
            ActiveCvProfileRepository activeCvProfiles,
            SitePresentationSettingsRepository settings,
            ActiveCvPublicAssetPort assets) {
        return new ActiveCvPublicQueryService(activeCvProfiles, settings, assets);
    }

    @Bean
    @ConditionalOnBean(ActiveCvPublicAssetPort.class)
    ActiveCvPublicDownloadService activeCvPublicDownloadService(
            ActiveCvProfileRepository activeCvProfiles,
            SitePresentationSettingsRepository settings,
            ActiveCvPublicAssetPort assets) {
        return new ActiveCvPublicDownloadService(activeCvProfiles, settings, assets);
    }
}
