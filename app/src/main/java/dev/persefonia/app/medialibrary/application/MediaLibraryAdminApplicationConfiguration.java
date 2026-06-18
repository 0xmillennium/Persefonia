package dev.persefonia.app.medialibrary.application;

import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.medialibrary.application.admin.MediaAdminCommandService;
import dev.persefonia.medialibrary.application.admin.MediaAdminQueryService;
import dev.persefonia.medialibrary.application.admin.MediaAdminReadModel;
import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.authorization.MediaCommandAuthorizationPolicy;
import dev.persefonia.medialibrary.application.processing.ProcessImageAssetCommandService;
import dev.persefonia.medialibrary.application.upload.UploadAssetCommandService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MediaLibraryAdminApplicationConfiguration {
    @Bean
    MediaCommandAuthorizationPolicy mediaCommandAuthorizationPolicy(AdminCommandAuthorizationPolicy policy) {
        return new IdentityAccessMediaCommandAuthorizationPolicy(policy);
    }

    @Bean
    @ConditionalOnBean({UploadAssetCommandService.class, ProcessImageAssetCommandService.class})
    MediaAdminCommandService mediaAdminCommandService(
            MediaCommandAuthorizationPolicy authorization,
            UploadAssetCommandService uploads,
            ProcessImageAssetCommandService processing,
            AssetRepository assets) {
        return new MediaAdminCommandService(
                authorization,
                uploads,
                processing,
                assets,
                Clock.systemUTC());
    }

    @Bean
    MediaAdminQueryService mediaAdminQueryService(MediaAdminReadModel readModel) {
        return new MediaAdminQueryService(readModel);
    }
}
