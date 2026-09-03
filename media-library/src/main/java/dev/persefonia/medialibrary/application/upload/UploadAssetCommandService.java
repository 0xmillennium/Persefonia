package dev.persefonia.medialibrary.application.upload;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.application.storage.AssetStorageRollbackCompensationPort;
import dev.persefonia.medialibrary.application.storage.FinalAssetStorageKey;
import dev.persefonia.medialibrary.application.storage.OriginalAssetStagingRequest;
import dev.persefonia.medialibrary.application.storage.StagedAssetObject;
import dev.persefonia.medialibrary.application.storage.StorageWriteException;
import dev.persefonia.medialibrary.application.storage.StoredAssetObject;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetValidationResult;
import dev.persefonia.medialibrary.domain.asset.AssetValidationResultId;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.Checksum;
import dev.persefonia.medialibrary.domain.asset.ContentTypeName;
import dev.persefonia.medialibrary.domain.asset.FileExtension;
import dev.persefonia.medialibrary.domain.asset.FileSize;
import dev.persefonia.medialibrary.domain.asset.OriginalFilename;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import dev.persefonia.medialibrary.domain.asset.StoredFilename;
import dev.persefonia.medialibrary.domain.asset.ValidationRuleName;
import dev.persefonia.medialibrary.domain.asset.ValidationStatus;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class UploadAssetCommandService {
    private final AssetRepository assetRepository;
    private final AssetStoragePort storage;
    private final UploadValidationPolicy validationPolicy;
    private final MediaContentSniffer contentSniffer;
    private final ChecksumCalculator checksumCalculator;
    private final Clock clock;
    private final AssetStorageRollbackCompensationPort rollbackCompensation;

    public UploadAssetCommandService(
            AssetRepository assetRepository,
            AssetStoragePort storage,
            UploadValidationPolicy validationPolicy,
            MediaContentSniffer contentSniffer,
            ChecksumCalculator checksumCalculator,
            Clock clock) {
        this(
                assetRepository,
                storage,
                validationPolicy,
                contentSniffer,
                checksumCalculator,
                clock,
                AssetStorageRollbackCompensationPort.noOp());
    }

    public UploadAssetCommandService(
            AssetRepository assetRepository,
            AssetStoragePort storage,
            UploadValidationPolicy validationPolicy,
            MediaContentSniffer contentSniffer,
            ChecksumCalculator checksumCalculator,
            Clock clock,
            AssetStorageRollbackCompensationPort rollbackCompensation) {
        this.assetRepository = Objects.requireNonNull(assetRepository, "assetRepository");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.validationPolicy = Objects.requireNonNull(validationPolicy, "validationPolicy");
        this.contentSniffer = Objects.requireNonNull(contentSniffer, "contentSniffer");
        this.checksumCalculator = Objects.requireNonNull(checksumCalculator, "checksumCalculator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rollbackCompensation = Objects.requireNonNull(rollbackCompensation, "rollbackCompensation");
    }

    public UploadAssetResult upload(UploadAssetCommand command) {
        UploadValidationPolicy.DeclaredValidation declared = validationPolicy.validateDeclared(command);
        if (!declared.accepted()) {
            return new UploadAssetResult.Rejected(declared.errors());
        }

        AllowedMediaType allowedType = declared.allowedMediaTypeOptional().orElseThrow();
        StagedAssetObject stagedObject;
        try {
            stagedObject = storage.stageOriginal(new OriginalAssetStagingRequest(
                    command.byteSource(), validationPolicy.maximumBytes(allowedType) + 1));
        } catch (StorageWriteException exception) {
            throw new UploadAssetException("Unable to stage uploaded media.", exception);
        }

        StoredAssetObject storedObject = null;
        try {
            DetectedMediaType detectedType = detect(stagedObject);
            List<UploadValidationError> errors =
                    validationPolicy.validateStaged(command, allowedType, detectedType, stagedObject.sizeBytes());
            if (!errors.isEmpty()) {
                storage.deleteStagedIfExists(stagedObject);
                return new UploadAssetResult.Rejected(errors);
            }

            String checksumValue;
            try {
                checksumValue = checksum(stagedObject);
            } catch (IOException exception) {
                storage.deleteStagedIfExists(stagedObject);
                return new UploadAssetResult.Rejected(List.of(new UploadValidationError(
                        UploadValidationErrorCode.CHECKSUM_CALCULATION_FAILED,
                        "The uploaded file checksum could not be calculated.")));
            }
            Checksum checksum = Checksum.of(checksumValue);
            var duplicate = assetRepository.findByChecksum(checksum);
            if (duplicate.isPresent()) {
                storage.deleteStagedIfExists(stagedObject);
                return new UploadAssetResult.Duplicate(duplicate.get().id());
            }

            AssetId assetId = AssetId.newId();
            String storedFilename = checksumValue + "." + allowedType.canonicalExtension();
            FinalAssetStorageKey finalKey =
                    new FinalAssetStorageKey("original/" + assetId.value() + "/" + storedFilename);
            storedObject = storage.commitStaged(stagedObject, finalKey);
            rollbackCompensation.deleteOnRollback(StoragePath.of(storedObject.logicalPath()));

            Instant now = clock.instant();
            List<AssetValidationResult> validationResults = acceptedValidationResults(now);
            Asset asset = createAsset(
                    command, allowedType, stagedObject.sizeBytes(), checksum, assetId,
                    storedFilename, storedObject, validationResults, now);
            assetRepository.save(asset);
            return new UploadAssetResult.Created(assetId);
        } catch (UploadAssetException exception) {
            cleanup(stagedObject, storedObject);
            throw exception;
        } catch (StorageWriteException exception) {
            cleanup(stagedObject, storedObject);
            throw new UploadAssetException("Unable to store uploaded media.", exception);
        } catch (RuntimeException exception) {
            cleanup(stagedObject, storedObject);
            throw exception;
        }
    }

    private void cleanup(StagedAssetObject stagedObject, StoredAssetObject storedObject) {
        if (storedObject == null) {
            storage.deleteStagedIfExists(stagedObject);
        } else {
            storage.deleteStoredIfExists(storedObject);
        }
    }

    private DetectedMediaType detect(StagedAssetObject stagedObject) {
        try (InputStream input = storage.openStaged(stagedObject)) {
            return contentSniffer.detect(input);
        } catch (IOException exception) {
            throw new UploadAssetException("Unable to inspect staged media.", exception);
        }
    }

    private String checksum(StagedAssetObject stagedObject) throws IOException {
        try (InputStream input = storage.openStaged(stagedObject)) {
            return checksumCalculator.calculate(input);
        }
    }

    private static Asset createAsset(
            UploadAssetCommand command,
            AllowedMediaType allowedType,
            long actualSize,
            Checksum checksum,
            AssetId assetId,
            String storedFilename,
            StoredAssetObject storedObject,
            List<AssetValidationResult> validationResults,
            Instant now) {
        if (allowedType.assetKind() == dev.persefonia.medialibrary.domain.asset.AssetKind.IMAGE) {
            return Asset.pendingImage(
                    assetId,
                    OriginalFilename.of(command.originalFilename()),
                    StoredFilename.of(storedFilename),
                    StoragePath.of(storedObject.logicalPath()),
                    null,
                    ContentTypeName.of(allowedType.contentType()),
                    FileExtension.of(allowedType.canonicalExtension()),
                    FileSize.of(actualSize),
                    checksum,
                    validationResults,
                    now);
        }
        return Asset.pdf(
                assetId,
                OriginalFilename.of(command.originalFilename()),
                StoredFilename.of(storedFilename),
                StoragePath.of(storedObject.logicalPath()),
                null,
                ContentTypeName.of(allowedType.contentType()),
                FileExtension.of(allowedType.canonicalExtension()),
                FileSize.of(actualSize),
                checksum,
                AssetVisibility.PRIVATE,
                validationResults,
                now);
    }

    private static List<AssetValidationResult> acceptedValidationResults(Instant now) {
        return List.of(
                passed("declared_content_type_allowed", now),
                passed("extension_allowed", now),
                passed("magic_bytes_match", now),
                passed("file_size_within_limit", now),
                passed("checksum_calculated", now));
    }

    private static AssetValidationResult passed(String rule, Instant now) {
        return new AssetValidationResult(
                AssetValidationResultId.newId(),
                ValidationRuleName.of(rule),
                ValidationStatus.PASSED,
                null,
                now);
    }
}
