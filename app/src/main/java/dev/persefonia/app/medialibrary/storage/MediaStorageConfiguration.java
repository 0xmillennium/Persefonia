package dev.persefonia.app.medialibrary.storage;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.application.upload.ChecksumCalculator;
import dev.persefonia.medialibrary.application.upload.MediaContentSniffer;
import dev.persefonia.medialibrary.application.upload.UploadAssetCommandService;
import dev.persefonia.medialibrary.application.upload.UploadValidationPolicy;
import dev.persefonia.app.medialibrary.processing.JavaImageIoImageMetadataReader;
import dev.persefonia.app.medialibrary.processing.JavaImageIoImageVariantGenerator;
import dev.persefonia.medialibrary.application.processing.ImageMetadataReader;
import dev.persefonia.medialibrary.application.processing.ImageVariantGenerator;
import dev.persefonia.medialibrary.application.processing.ProcessImageAssetCommandService;
import dev.persefonia.medialibrary.application.publicview.PublicImageAssetQueryService;
import dev.persefonia.medialibrary.application.publicview.PublicImageVariantContentService;
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
    MediaStorageReadinessService mediaStorageReadinessService(MediaStorageProperties properties) {
        MediaStorageReadinessService readiness =
                new MediaStorageReadinessService(properties.requireStorageRootPath());
        readiness.verifyReady();
        return readiness;
    }

    @Bean
    AssetStoragePort assetStoragePort(MediaStorageReadinessService readiness) {
        return new LocalFileAssetStorageAdapter(readiness.storageRoot());
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
    ImageMetadataReader imageMetadataReader(MediaStorageProperties properties) {
        return new JavaImageIoImageMetadataReader(properties.getMaxImagePixels());
    }

    @Bean
    ImageVariantGenerator imageVariantGenerator() {
        return new JavaImageIoImageVariantGenerator();
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

    @Bean
    ProcessImageAssetCommandService processImageAssetCommandService(
            AssetRepository assetRepository,
            AssetStoragePort storage,
            ImageMetadataReader metadataReader,
            ImageVariantGenerator variantGenerator,
            ChecksumCalculator checksumCalculator) {
        return new ProcessImageAssetCommandService(
                assetRepository,
                storage,
                metadataReader,
                variantGenerator,
                checksumCalculator,
                Clock.systemUTC());
    }

    @Bean
    PublicImageAssetQueryService publicImageAssetQueryService(
            AssetRepository assetRepository,
            AssetStoragePort storage) {
        return new PublicImageAssetQueryService(assetRepository, storage);
    }

    @Bean
    PublicImageVariantContentService publicImageVariantContentService(
            AssetRepository assetRepository,
            AssetStoragePort storage) {
        return new PublicImageVariantContentService(assetRepository, storage);
    }
}
