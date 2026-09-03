package dev.persefonia.medialibrary.application.processing;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import dev.persefonia.medialibrary.application.storage.AssetStorageRollbackCompensationPort;
import dev.persefonia.medialibrary.application.storage.StoredAssetObject;
import dev.persefonia.medialibrary.application.storage.VariantStorageRequest;
import dev.persefonia.medialibrary.application.upload.ChecksumCalculator;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
import dev.persefonia.medialibrary.domain.asset.AssetValidationResult;
import dev.persefonia.medialibrary.domain.asset.AssetValidationResultId;
import dev.persefonia.medialibrary.domain.asset.AssetVariant;
import dev.persefonia.medialibrary.domain.asset.AssetVariantId;
import dev.persefonia.medialibrary.domain.asset.Checksum;
import dev.persefonia.medialibrary.domain.asset.FileSize;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import dev.persefonia.medialibrary.domain.asset.ValidationMessage;
import dev.persefonia.medialibrary.domain.asset.ValidationRuleName;
import dev.persefonia.medialibrary.domain.asset.ValidationStatus;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ProcessImageAssetCommandService {
    private final AssetRepository assetRepository;
    private final AssetStoragePort storage;
    private final ImageMetadataReader metadataReader;
    private final ImageVariantGenerator variantGenerator;
    private final ChecksumCalculator checksumCalculator;
    private final Clock clock;
    private final AssetStorageRollbackCompensationPort rollbackCompensation;

    public ProcessImageAssetCommandService(
            AssetRepository assetRepository,
            AssetStoragePort storage,
            ImageMetadataReader metadataReader,
            ImageVariantGenerator variantGenerator,
            ChecksumCalculator checksumCalculator,
            Clock clock) {
        this(
                assetRepository,
                storage,
                metadataReader,
                variantGenerator,
                checksumCalculator,
                clock,
                AssetStorageRollbackCompensationPort.noOp());
    }

    public ProcessImageAssetCommandService(
            AssetRepository assetRepository,
            AssetStoragePort storage,
            ImageMetadataReader metadataReader,
            ImageVariantGenerator variantGenerator,
            ChecksumCalculator checksumCalculator,
            Clock clock,
            AssetStorageRollbackCompensationPort rollbackCompensation) {
        this.assetRepository = Objects.requireNonNull(assetRepository, "assetRepository");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.metadataReader = Objects.requireNonNull(metadataReader, "metadataReader");
        this.variantGenerator = Objects.requireNonNull(variantGenerator, "variantGenerator");
        this.checksumCalculator = Objects.requireNonNull(checksumCalculator, "checksumCalculator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rollbackCompensation = Objects.requireNonNull(rollbackCompensation, "rollbackCompensation");
    }

    public ProcessImageAssetResult process(ProcessImageAssetCommand command) {
        Objects.requireNonNull(command, "command");
        var loaded = assetRepository.findById(command.assetId());
        if (loaded.isEmpty()) {
            return new ProcessImageAssetResult.NotFound(command.assetId());
        }

        Asset asset = loaded.get();
        if (asset.kind() != AssetKind.IMAGE) {
            return new ProcessImageAssetResult.NotProcessable(asset.id());
        }
        if (asset.processingStatus() == ProcessingStatus.PROCESSED) {
            return new ProcessImageAssetResult.AlreadyProcessed(asset.id());
        }

        List<StoragePath> storedVariantPaths = new ArrayList<>();
        ImageMetadata metadata;
        List<AssetVariant> variants;
        Instant now = clock.instant();
        try {
            byte[] originalBytes = readOriginal(asset);
            metadata = metadataReader.read(originalBytes);
            List<GeneratedImageVariant> generated = variantGenerator.generate(
                    new ImageVariantGenerationRequest(
                            originalBytes, asset.contentType(), ImageVariantSpecs.all()));
            variants = storeVariants(asset, generated, storedVariantPaths, now);
        } catch (RuntimeException exception) {
            cleanup(storedVariantPaths);
            String reason = safeFailureReason(exception);
            asset.markFailed(mergeValidationResults(
                    asset.validationResults(),
                    failed("image_processing", reason, now)), now);
            assetRepository.save(asset);
            return new ProcessImageAssetResult.Failed(asset.id(), reason);
        }

        asset.markProcessed(
                metadata.dimensions(),
                variants,
                mergeValidationResults(
                        asset.validationResults(),
                        passed("image_decode", now),
                        passed("image_dimensions_read", now),
                        passed("image_variants_generated", now)),
                now);
        try {
            assetRepository.save(asset);
        } catch (RuntimeException exception) {
            cleanup(storedVariantPaths);
            throw exception;
        }
        return new ProcessImageAssetResult.Processed(asset.id());
    }

    private byte[] readOriginal(Asset asset) {
        try (InputStream input = storage.openStored(asset.storagePath())) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new ImageProcessingException("Stored image could not be read.", exception);
        }
    }

    private List<AssetVariant> storeVariants(
            Asset asset,
            List<GeneratedImageVariant> generated,
            List<StoragePath> storedVariantPaths,
            Instant now) {
        Map<String, GeneratedImageVariant> byName = new LinkedHashMap<>();
        for (GeneratedImageVariant variant : generated) {
            if (byName.put(variant.name().databaseValue(), variant) != null) {
                throw new ImageProcessingException("Image generator returned a duplicate variant.");
            }
        }
        if (byName.size() != ImageVariantSpecs.all().size()
                || ImageVariantSpecs.all().stream()
                        .anyMatch(spec -> !byName.containsKey(spec.name().databaseValue()))) {
            throw new ImageProcessingException("Image generator did not return all required variants.");
        }

        List<AssetVariant> variants = new ArrayList<>();
        for (ImageVariantSpec spec : ImageVariantSpecs.all()) {
            GeneratedImageVariant generatedVariant = byName.get(spec.name().databaseValue());
            byte[] bytes = generatedVariant.bytes();
            String checksumValue = checksum(bytes);
            StoragePath storagePath = StoragePath.of(
                    "variants/" + asset.id().value() + "/" + spec.name().databaseValue()
                            + "-" + checksumValue + "." + generatedVariant.fileExtension().value());
            StoredAssetObject stored = storage.storeVariant(new VariantStorageRequest(storagePath, bytes));
            StoragePath storedPath = StoragePath.of(stored.logicalPath());
            storedVariantPaths.add(storedPath);
            rollbackCompensation.deleteOnRollback(storedPath);
            variants.add(new AssetVariant(
                    AssetVariantId.newId(),
                    generatedVariant.name(),
                    generatedVariant.width(),
                    generatedVariant.height(),
                    generatedVariant.contentType(),
                    FileSize.of(bytes.length),
                    storedPath,
                    null,
                    Checksum.of(checksumValue),
                    now));
        }
        return List.copyOf(variants);
    }

    private String checksum(byte[] bytes) {
        try (InputStream input = new java.io.ByteArrayInputStream(bytes)) {
            return checksumCalculator.calculate(input);
        } catch (IOException exception) {
            throw new ImageProcessingException("Generated image checksum could not be calculated.", exception);
        }
    }

    private void cleanup(List<StoragePath> paths) {
        for (StoragePath path : paths) {
            storage.deleteStoredByPathIfExists(path);
        }
    }

    private static List<AssetValidationResult> mergeValidationResults(
            List<AssetValidationResult> existing,
            AssetValidationResult... replacements) {
        Map<String, AssetValidationResult> merged = new LinkedHashMap<>();
        for (AssetValidationResult result : existing) {
            merged.put(result.rule().value(), result);
        }
        for (AssetValidationResult replacement : replacements) {
            merged.put(replacement.rule().value(), replacement);
        }
        return List.copyOf(merged.values());
    }

    private static AssetValidationResult passed(String rule, Instant now) {
        return new AssetValidationResult(
                AssetValidationResultId.newId(),
                ValidationRuleName.of(rule),
                ValidationStatus.PASSED,
                null,
                now);
    }

    private static AssetValidationResult failed(String rule, String message, Instant now) {
        return new AssetValidationResult(
                AssetValidationResultId.newId(),
                ValidationRuleName.of(rule),
                ValidationStatus.FAILED,
                ValidationMessage.of(message),
                now);
    }

    private static String safeFailureReason(RuntimeException exception) {
        if (exception instanceof ImageProcessingException && exception.getMessage() != null) {
            return exception.getMessage();
        }
        return "Image processing failed.";
    }
}
