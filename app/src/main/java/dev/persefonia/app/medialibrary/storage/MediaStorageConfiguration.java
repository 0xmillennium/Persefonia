package dev.persefonia.app.medialibrary.storage;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.application.upload.ChecksumCalculator;
import dev.persefonia.medialibrary.application.upload.MediaContentSniffer;
import dev.persefonia.medialibrary.application.upload.UploadAssetCommandService;
import dev.persefonia.medialibrary.application.upload.UploadValidationPolicy;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MediaStorageProperties.class)
@ConditionalOnProperty(prefix = "persefonia.media", name = "storage-root")
public class MediaStorageConfiguration {
    @Bean
    AssetStoragePort assetStoragePort(MediaStorageProperties properties) {
        return new LocalFileAssetStorageAdapter(Path.of(properties.getStorageRoot()));
    }

    @Bean
    UploadValidationPolicy uploadValidationPolicy(MediaStorageProperties properties) {
        return new UploadValidationPolicy(properties.getMaxImageBytes(), properties.getMaxPdfBytes());
    }

    @Bean
    MediaContentSniffer mediaContentSniffer() {
        return new MediaContentSniffer();
    }

    @Bean
    ChecksumCalculator checksumCalculator() {
        return new ChecksumCalculator();
    }

    @Bean
    UploadAssetCommandService uploadAssetCommandService(
            AssetRepository assetRepository,
            AssetStoragePort storage,
            UploadValidationPolicy validationPolicy,
            MediaContentSniffer contentSniffer,
            ChecksumCalculator checksumCalculator) {
        return new UploadAssetCommandService(
                assetRepository,
                storage,
                validationPolicy,
                contentSniffer,
                checksumCalculator,
                Clock.systemUTC());
    }
}
