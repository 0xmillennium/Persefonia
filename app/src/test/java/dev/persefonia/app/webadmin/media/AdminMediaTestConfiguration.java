package dev.persefonia.app.webadmin.media;

import dev.persefonia.medialibrary.application.admin.AdminUploadAssetCommand;
import dev.persefonia.medialibrary.application.admin.AdminUploadAssetResult;
import dev.persefonia.medialibrary.application.admin.AssetMetadataUpdateResult;
import dev.persefonia.medialibrary.application.admin.MediaAdminAssetDetails;
import dev.persefonia.medialibrary.application.admin.MediaAdminAssetListItem;
import dev.persefonia.medialibrary.application.admin.MediaAdminAssetValidationResultDetails;
import dev.persefonia.medialibrary.application.admin.MediaAdminAssetVariantDetails;
import dev.persefonia.medialibrary.application.admin.MediaAdminCommandGateway;
import dev.persefonia.medialibrary.application.admin.MediaAdminCommandService;
import dev.persefonia.medialibrary.application.admin.MediaAdminReadModel;
import dev.persefonia.medialibrary.application.admin.UpdateAssetMetadataCommand;
import dev.persefonia.medialibrary.application.authorization.MediaCommandAuthorizationPolicy;
import dev.persefonia.medialibrary.application.upload.UploadValidationError;
import dev.persefonia.medialibrary.application.upload.UploadValidationErrorCode;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
class AdminMediaTestConfiguration {
    static final AssetId IMAGE_ID = AssetId.from(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    static final AssetId PDF_ID = AssetId.from(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    static final AssetId FAILED_ID = AssetId.from(UUID.fromString("33333333-3333-3333-3333-333333333333"));
    static final AssetId DUPLICATE_ID = AssetId.from(UUID.fromString("44444444-4444-4444-4444-444444444444"));
    static final AssetId MISSING_ID = AssetId.from(UUID.fromString("99999999-9999-9999-9999-999999999999"));

    @Bean
    @Primary
    AdminMediaReadModelStub adminMediaReadModelStub() {
        return new AdminMediaReadModelStub();
    }

    @Bean
    @Primary
    AdminMediaGatewayStub adminMediaGatewayStub(MediaCommandAuthorizationPolicy authorization) {
        return new AdminMediaGatewayStub(authorization);
    }

    static final class AdminMediaReadModelStub implements MediaAdminReadModel {
        private final Map<AssetId, MediaAdminAssetDetails> assets = new LinkedHashMap<>();

        void reset() {
            assets.clear();
        }

        void add(MediaAdminAssetDetails asset) {
            assets.put(asset.summary().assetId(), asset);
        }

        @Override
        public List<MediaAdminAssetListItem> listAssets() {
            return assets.values().stream().map(MediaAdminAssetDetails::summary).toList();
        }

        @Override
        public Optional<MediaAdminAssetDetails> findAssetDetails(AssetId id) {
            return Optional.ofNullable(assets.get(id));
        }
    }

    static final class AdminMediaGatewayStub implements MediaAdminCommandGateway {
        private final MediaCommandAuthorizationPolicy authorization;
        private AdminUploadAssetResult uploadResult =
                new AdminUploadAssetResult.Created(IMAGE_ID, ProcessingStatus.PROCESSED, null);
        private AssetMetadataUpdateResult updateResult = new AssetMetadataUpdateResult.Updated(IMAGE_ID);
        private final List<AdminUploadAssetCommand> uploadMutations = new ArrayList<>();
        private final List<UpdateAssetMetadataCommand> updateMutations = new ArrayList<>();

        AdminMediaGatewayStub(MediaCommandAuthorizationPolicy authorization) {
            this.authorization = authorization;
        }

        void reset() {
            uploadResult = new AdminUploadAssetResult.Created(IMAGE_ID, ProcessingStatus.PROCESSED, null);
            updateResult = new AssetMetadataUpdateResult.Updated(IMAGE_ID);
            uploadMutations.clear();
            updateMutations.clear();
        }

        void nextUploadResult(AdminUploadAssetResult result) {
            uploadResult = result;
        }

        void nextUpdateResult(AssetMetadataUpdateResult result) {
            updateResult = result;
        }

        int uploadMutationCount() {
            return uploadMutations.size();
        }

        int updateMutationCount() {
            return updateMutations.size();
        }

        @Override
        public AdminUploadAssetResult upload(AdminUploadAssetCommand command) {
            authorization.requireOwner(command.actor(), MediaAdminCommandService.UPLOAD_COMMAND);
            uploadMutations.add(command);
            return uploadResult;
        }

        @Override
        public AssetMetadataUpdateResult updateMetadata(UpdateAssetMetadataCommand command) {
            authorization.requireOwner(command.actor(), MediaAdminCommandService.UPDATE_METADATA_COMMAND);
            updateMutations.add(command);
            return updateResult;
        }
    }

    static MediaAdminAssetDetails processedImage() {
        return image(IMAGE_ID, "hero.png", AssetVisibility.PRIVATE, ProcessingStatus.PROCESSED, "Hero image", false);
    }

    static MediaAdminAssetDetails failedImage() {
        return image(FAILED_ID, "broken.png", AssetVisibility.PRIVATE, ProcessingStatus.FAILED, null, false);
    }

    static MediaAdminAssetDetails pdf() {
        MediaAdminAssetListItem summary = new MediaAdminAssetListItem(
                PDF_ID,
                "cv.pdf",
                AssetKind.PDF,
                AssetVisibility.PRIVATE,
                ProcessingStatus.NOT_REQUIRED,
                "application/pdf",
                "pdf",
                1234,
                "pdfchecksum1234567890",
                null,
                null,
                now().minusSeconds(60),
                now());
        return new MediaAdminAssetDetails(summary, null, false, List.of(), validationResults());
    }

    static MediaAdminAssetDetails duplicateImage() {
        return image(DUPLICATE_ID, "duplicate.png", AssetVisibility.PRIVATE, ProcessingStatus.PROCESSED, "Duplicate", false);
    }

    static UploadValidationError rejectedUploadError() {
        return new UploadValidationError(
                UploadValidationErrorCode.CONTENT_TYPE_NOT_ALLOWED,
                "Choose a JPEG, PNG, or PDF file.");
    }

    private static MediaAdminAssetDetails image(
            AssetId id,
            String filename,
            AssetVisibility visibility,
            ProcessingStatus processingStatus,
            String altText,
            boolean decorative) {
        MediaAdminAssetListItem summary = new MediaAdminAssetListItem(
                id,
                filename,
                AssetKind.IMAGE,
                visibility,
                processingStatus,
                "image/png",
                "png",
                2048,
                "imagechecksum1234567890",
                processingStatus == ProcessingStatus.PROCESSED ? 1200 : null,
                processingStatus == ProcessingStatus.PROCESSED ? 800 : null,
                now().minusSeconds(120),
                now());
        return new MediaAdminAssetDetails(
                summary,
                altText,
                decorative,
                processingStatus == ProcessingStatus.PROCESSED ? variants(id, visibility) : List.of(),
                validationResults());
    }

    private static List<MediaAdminAssetVariantDetails> variants(AssetId assetId, AssetVisibility visibility) {
        String publicRoute = visibility == AssetVisibility.PUBLIC
                ? "/media/assets/" + assetId.value() + "/variants/thumbnail"
                : null;
        return List.of(new MediaAdminAssetVariantDetails(
                "thumbnail", 320, 200, "image/png", 512, "variantchecksum1234567890", publicRoute));
    }

    private static List<MediaAdminAssetValidationResultDetails> validationResults() {
        return List.of(new MediaAdminAssetValidationResultDetails(
                "image_decode", "PASSED", "Decoded successfully", now()));
    }

    private static Instant now() {
        return Instant.parse("2026-06-18T10:00:00Z");
    }
}
