package dev.persefonia.app.medialibrary.application;

import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.medialibrary.application.admin.MediaAdminQueryService;
import dev.persefonia.medialibrary.application.admin.MediaAdminReadModel;
import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.authorization.MediaCommandAuthorizationPolicy;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetContentService;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetQueryService;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetReadModel;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
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
    MediaAdminQueryService mediaAdminQueryService(MediaAdminReadModel readModel) {
        return new MediaAdminQueryService(readModel);
    }

    @Bean
    @ConditionalOnBean(PublicPdfAssetReadModel.class)
    PublicPdfAssetQueryService publicPdfAssetQueryService(PublicPdfAssetReadModel readModel) {
        return new PublicPdfAssetQueryService(readModel);
    }

    @Bean
    @ConditionalOnBean({AssetRepository.class, AssetStoragePort.class})
    PublicPdfAssetContentService publicPdfAssetContentService(
            AssetRepository assets, AssetStoragePort storage) {
        return new PublicPdfAssetContentService(assets, storage);
    }
}
