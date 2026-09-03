package dev.persefonia.app.medialibrary.storage;

import dev.persefonia.app.audit.integration.MediaAuditMapper;
import dev.persefonia.app.medialibrary.application.SpringAssetStorageRollbackCompensationAdapter;
import dev.persefonia.app.medialibrary.application.TransactionalMediaAdminCommandGateway;
import dev.persefonia.app.medialibrary.processing.JavaImageIoImageMetadataReader;
import dev.persefonia.app.medialibrary.processing.JavaImageIoImageVariantGenerator;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.medialibrary.application.admin.MediaAdminCommandService;
import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.authorization.MediaCommandAuthorizationPolicy;
import dev.persefonia.medialibrary.application.processing.ImageMetadataReader;
import dev.persefonia.medialibrary.application.processing.ImageVariantGenerator;
import dev.persefonia.medialibrary.application.processing.ProcessImageAssetCommandService;
import dev.persefonia.medialibrary.application.publicview.PublicImageAssetQueryService;
import dev.persefonia.medialibrary.application.publicview.PublicImageVariantContentService;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.application.storage.AssetStorageRollbackCompensationPort;
import dev.persefonia.medialibrary.application.upload.ChecksumCalculator;
import dev.persefonia.medialibrary.application.upload.MediaContentSniffer;
import dev.persefonia.medialibrary.application.upload.UploadAssetCommandService;
import dev.persefonia.medialibrary.application.upload.UploadValidationPolicy;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
    AssetStorageRollbackCompensationPort assetStorageRollbackCompensationPort(AssetStoragePort storage) {
        return new SpringAssetStorageRollbackCompensationAdapter(storage);
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
            ChecksumCalculator checksumCalculator,
            AssetStorageRollbackCompensationPort rollbackCompensation) {
        return new UploadAssetCommandService(
                assetRepository,
                storage,
                validationPolicy,
                contentSniffer,
                checksumCalculator,
                Clock.systemUTC(),
                rollbackCompensation);
    }

    @Bean
    ProcessImageAssetCommandService processImageAssetCommandService(
            AssetRepository assetRepository,
            AssetStoragePort storage,
            ImageMetadataReader metadataReader,
            ImageVariantGenerator variantGenerator,
            ChecksumCalculator checksumCalculator,
            AssetStorageRollbackCompensationPort rollbackCompensation) {
        return new ProcessImageAssetCommandService(
                assetRepository,
                storage,
                metadataReader,
                variantGenerator,
                checksumCalculator,
                Clock.systemUTC(),
                rollbackCompensation);
    }

    @Bean
    MediaAdminCommandService mediaAdminCommandService(
            MediaCommandAuthorizationPolicy authorization,
            UploadAssetCommandService uploads,
            ProcessImageAssetCommandService processing,
            AssetRepository assets) {
        return new MediaAdminCommandService(
                authorization, uploads, processing, assets, Clock.systemUTC());
    }

    @Bean
    @Primary
    TransactionalMediaAdminCommandGateway transactionalMediaAdminCommandGateway(
            MediaAdminCommandService service,
            AppendAuditRecordPort audit,
            MediaAuditMapper auditMapper) {
        return new TransactionalMediaAdminCommandGateway(service, audit, auditMapper);
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
