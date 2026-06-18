package dev.persefonia.medialibrary.domain.asset;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class Asset {
    private final AssetId id;
    private final OriginalFilename originalFilename;
    private final StoredFilename storedFilename;
    private final StoragePath storagePath;
    private final PublicAssetUrl publicUrl;
    private final ContentTypeName contentType;
    private final FileExtension fileExtension;
    private final FileSize sizeBytes;
    private final Checksum checksum;
    private final AssetKind kind;
    private AssetVisibility visibility;
    private ImageDimensions imageDimensions;
    private AltText altText;
    private DecorativeImageFlag decorative;
    private ProcessingStatus processingStatus;
    private List<AssetVariant> variants;
    private List<AssetValidationResult> validationResults;
    private final Instant createdAt;
    private Instant updatedAt;
    private Version version;

    private Asset(
            AssetId id,
            OriginalFilename originalFilename,
            StoredFilename storedFilename,
            StoragePath storagePath,
            PublicAssetUrl publicUrl,
            ContentTypeName contentType,
            FileExtension fileExtension,
            FileSize sizeBytes,
            Checksum checksum,
            AssetKind kind,
            AssetVisibility visibility,
            ImageDimensions imageDimensions,
            AltText altText,
            DecorativeImageFlag decorative,
            ProcessingStatus processingStatus,
            List<AssetVariant> variants,
            List<AssetValidationResult> validationResults,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        this.id = Objects.requireNonNull(id, "id");
        this.originalFilename = Objects.requireNonNull(originalFilename, "originalFilename");
        this.storedFilename = Objects.requireNonNull(storedFilename, "storedFilename");
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath");
        this.publicUrl = publicUrl;
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.fileExtension = Objects.requireNonNull(fileExtension, "fileExtension");
        this.sizeBytes = Objects.requireNonNull(sizeBytes, "sizeBytes");
        this.checksum = Objects.requireNonNull(checksum, "checksum");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.imageDimensions = imageDimensions;
        this.altText = altText;
        this.decorative = Objects.requireNonNull(decorative, "decorative");
        this.processingStatus = Objects.requireNonNull(processingStatus, "processingStatus");
        this.variants = List.copyOf(Objects.requireNonNull(variants, "variants"));
        this.validationResults = List.copyOf(Objects.requireNonNull(validationResults, "validationResults"));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = Objects.requireNonNull(version, "version");
        validateInvariants();
    }

    public static Asset pendingImage(
            AssetId id,
            OriginalFilename originalFilename,
            StoredFilename storedFilename,
            StoragePath storagePath,
            PublicAssetUrl publicUrl,
            ContentTypeName contentType,
            FileExtension fileExtension,
            FileSize sizeBytes,
            Checksum checksum,
            Instant now) {
        return pendingImage(
                id, originalFilename, storedFilename, storagePath, publicUrl, contentType, fileExtension,
                sizeBytes, checksum, List.of(), now);
    }

    public static Asset pendingImage(
            AssetId id,
            OriginalFilename originalFilename,
            StoredFilename storedFilename,
            StoragePath storagePath,
            PublicAssetUrl publicUrl,
            ContentTypeName contentType,
            FileExtension fileExtension,
            FileSize sizeBytes,
            Checksum checksum,
            List<AssetValidationResult> validationResults,
            Instant now) {
        return new Asset(
                id, originalFilename, storedFilename, storagePath, publicUrl, contentType, fileExtension,
                sizeBytes, checksum, AssetKind.IMAGE, AssetVisibility.PRIVATE, null, null,
                DecorativeImageFlag.informative(), ProcessingStatus.PENDING, List.of(), validationResults,
                now, now, Version.initial());
    }

    public static Asset processedImage(
            AssetId id,
            OriginalFilename originalFilename,
            StoredFilename storedFilename,
            StoragePath storagePath,
            PublicAssetUrl publicUrl,
            ContentTypeName contentType,
            FileExtension fileExtension,
            FileSize sizeBytes,
            Checksum checksum,
            AssetVisibility visibility,
            ImageDimensions dimensions,
            AltText altText,
            DecorativeImageFlag decorative,
            List<AssetVariant> variants,
            List<AssetValidationResult> validationResults,
            Instant now) {
        return new Asset(
                id, originalFilename, storedFilename, storagePath, publicUrl, contentType, fileExtension,
                sizeBytes, checksum, AssetKind.IMAGE, visibility, dimensions, altText, decorative,
                ProcessingStatus.PROCESSED, variants, validationResults, now, now, Version.initial());
    }

    public static Asset pdf(
            AssetId id,
            OriginalFilename originalFilename,
            StoredFilename storedFilename,
            StoragePath storagePath,
            PublicAssetUrl publicUrl,
            ContentTypeName contentType,
            FileExtension fileExtension,
            FileSize sizeBytes,
            Checksum checksum,
            AssetVisibility visibility,
            List<AssetValidationResult> validationResults,
            Instant now) {
        return nonImage(
                id, originalFilename, storedFilename, storagePath, publicUrl, contentType, fileExtension,
                sizeBytes, checksum, AssetKind.PDF, visibility, validationResults, now);
    }

    public static Asset document(
            AssetId id,
            OriginalFilename originalFilename,
            StoredFilename storedFilename,
            StoragePath storagePath,
            PublicAssetUrl publicUrl,
            ContentTypeName contentType,
            FileExtension fileExtension,
            FileSize sizeBytes,
            Checksum checksum,
            AssetVisibility visibility,
            List<AssetValidationResult> validationResults,
            Instant now) {
        return nonImage(
                id, originalFilename, storedFilename, storagePath, publicUrl, contentType, fileExtension,
                sizeBytes, checksum, AssetKind.DOCUMENT, visibility, validationResults, now);
    }

    private static Asset nonImage(
            AssetId id,
            OriginalFilename originalFilename,
            StoredFilename storedFilename,
            StoragePath storagePath,
            PublicAssetUrl publicUrl,
            ContentTypeName contentType,
            FileExtension fileExtension,
            FileSize sizeBytes,
            Checksum checksum,
            AssetKind kind,
            AssetVisibility visibility,
            List<AssetValidationResult> validationResults,
            Instant now) {
        return new Asset(
                id, originalFilename, storedFilename, storagePath, publicUrl, contentType, fileExtension,
                sizeBytes, checksum, kind, visibility, null, null, DecorativeImageFlag.informative(),
                ProcessingStatus.NOT_REQUIRED, List.of(), validationResults, now, now, Version.initial());
    }

    public static Asset rehydrate(
            AssetId id,
            OriginalFilename originalFilename,
            StoredFilename storedFilename,
            StoragePath storagePath,
            PublicAssetUrl publicUrl,
            ContentTypeName contentType,
            FileExtension fileExtension,
            FileSize sizeBytes,
            Checksum checksum,
            AssetKind kind,
            AssetVisibility visibility,
            ImageDimensions imageDimensions,
            AltText altText,
            DecorativeImageFlag decorative,
            ProcessingStatus processingStatus,
            List<AssetVariant> variants,
            List<AssetValidationResult> validationResults,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        return new Asset(
                id, originalFilename, storedFilename, storagePath, publicUrl, contentType, fileExtension,
                sizeBytes, checksum, kind, visibility, imageDimensions, altText, decorative,
                processingStatus, variants, validationResults, createdAt, updatedAt, version);
    }

    public void updateAccessibility(AltText altText, DecorativeImageFlag decorative, Instant now) {
        Objects.requireNonNull(decorative, "decorative");
        validateState(visibility, processingStatus, imageDimensions, altText, decorative, variants);
        this.altText = altText;
        this.decorative = decorative;
        markUpdated(now);
    }

    public void makePublic(Instant now) {
        validateState(AssetVisibility.PUBLIC, processingStatus, imageDimensions, altText, decorative, variants);
        visibility = AssetVisibility.PUBLIC;
        markUpdated(now);
    }

    public void makePrivate(Instant now) {
        visibility = AssetVisibility.PRIVATE;
        markUpdated(now);
    }

    public void replaceVariants(List<AssetVariant> variants, Instant now) {
        List<AssetVariant> replacement = List.copyOf(Objects.requireNonNull(variants, "variants"));
        validateState(visibility, processingStatus, imageDimensions, altText, decorative, replacement);
        this.variants = replacement;
        markUpdated(now);
    }

    public void replaceValidationResults(List<AssetValidationResult> validationResults, Instant now) {
        List<AssetValidationResult> replacement =
                List.copyOf(Objects.requireNonNull(validationResults, "validationResults"));
        rejectDuplicate(replacement, result -> result.rule().value(), "validation rule name");
        this.validationResults = replacement;
        markUpdated(now);
    }

    public void markProcessed(ImageDimensions dimensions, Instant now) {
        markProcessed(dimensions, variants, validationResults, now);
    }

    public void markProcessed(
            ImageDimensions dimensions,
            List<AssetVariant> processedVariants,
            List<AssetValidationResult> processedValidationResults,
            Instant now) {
        if (kind != AssetKind.IMAGE) {
            throw new AssetValidationException("only images may be marked processed");
        }
        List<AssetVariant> replacementVariants =
                List.copyOf(Objects.requireNonNull(processedVariants, "processedVariants"));
        List<AssetValidationResult> replacementValidationResults =
                List.copyOf(Objects.requireNonNull(processedValidationResults, "processedValidationResults"));
        validateState(
                visibility, ProcessingStatus.PROCESSED, dimensions, altText, decorative, replacementVariants);
        rejectDuplicate(replacementValidationResults, result -> result.rule().value(), "validation rule name");
        imageDimensions = Objects.requireNonNull(dimensions, "dimensions");
        variants = replacementVariants;
        validationResults = replacementValidationResults;
        processingStatus = ProcessingStatus.PROCESSED;
        markUpdated(now);
    }

    public void markFailed(List<AssetValidationResult> failedValidationResults, Instant now) {
        if (kind != AssetKind.IMAGE) {
            throw new AssetValidationException("only images may be marked failed");
        }
        List<AssetValidationResult> replacementValidationResults =
                List.copyOf(Objects.requireNonNull(failedValidationResults, "failedValidationResults"));
        rejectDuplicate(replacementValidationResults, result -> result.rule().value(), "validation rule name");
        validateState(
                AssetVisibility.PRIVATE,
                ProcessingStatus.FAILED,
                null,
                altText,
                decorative,
                List.of());
        visibility = AssetVisibility.PRIVATE;
        imageDimensions = null;
        variants = List.of();
        validationResults = replacementValidationResults;
        processingStatus = ProcessingStatus.FAILED;
        markUpdated(now);
    }

    private void markUpdated(Instant now) {
        Instant replacement = Objects.requireNonNull(now, "now");
        if (replacement.isBefore(createdAt)) {
            throw new AssetValidationException("updatedAt must not be before createdAt");
        }
        updatedAt = replacement;
        version = version.next();
    }

    private void validateInvariants() {
        if (updatedAt.isBefore(createdAt)) {
            throw new AssetValidationException("updatedAt must not be before createdAt");
        }
        validateState(visibility, processingStatus, imageDimensions, altText, decorative, variants);
        rejectDuplicate(validationResults, result -> result.rule().value(), "validation rule name");
    }

    private void validateState(
            AssetVisibility candidateVisibility,
            ProcessingStatus candidateStatus,
            ImageDimensions candidateDimensions,
            AltText candidateAltText,
            DecorativeImageFlag candidateDecorative,
            List<AssetVariant> candidateVariants) {
        if (kind == AssetKind.IMAGE) {
            if (candidateStatus == ProcessingStatus.NOT_REQUIRED) {
                throw new AssetValidationException("image processing status must not be NOT_REQUIRED");
            }
            if (candidateStatus == ProcessingStatus.PROCESSED && candidateDimensions == null) {
                throw new AssetValidationException("processed image must have dimensions");
            }
            if (candidateVisibility == AssetVisibility.PUBLIC && candidateStatus != ProcessingStatus.PROCESSED) {
                throw new AssetValidationException("public image must be processed");
            }
            if (candidateVisibility == AssetVisibility.PUBLIC
                    && !candidateDecorative.value()
                    && candidateAltText == null) {
                throw new AssetValidationException("public image must have alt text or be decorative");
            }
        } else {
            if (candidateStatus != ProcessingStatus.NOT_REQUIRED) {
                throw new AssetValidationException("PDF and document processing status must be NOT_REQUIRED");
            }
            if (!candidateVariants.isEmpty()) {
                throw new AssetValidationException("PDF and document assets must not have variants");
            }
        }
        rejectDuplicate(candidateVariants, AssetVariant::name, "variant name");
    }

    private static <T, K> void rejectDuplicate(List<T> values, Function<T, K> key, String label) {
        Set<K> seen = new HashSet<>();
        for (T value : values) {
            Objects.requireNonNull(value, label);
            if (!seen.add(key.apply(value))) {
                throw new AssetValidationException("duplicate " + label);
            }
        }
    }

    public AssetId id() {
        return id;
    }

    public OriginalFilename originalFilename() {
        return originalFilename;
    }

    public StoredFilename storedFilename() {
        return storedFilename;
    }

    public StoragePath storagePath() {
        return storagePath;
    }

    public Optional<PublicAssetUrl> publicUrl() {
        return Optional.ofNullable(publicUrl);
    }

    public ContentTypeName contentType() {
        return contentType;
    }

    public FileExtension fileExtension() {
        return fileExtension;
    }

    public FileSize sizeBytes() {
        return sizeBytes;
    }

    public Checksum checksum() {
        return checksum;
    }

    public AssetKind kind() {
        return kind;
    }

    public AssetVisibility visibility() {
        return visibility;
    }

    public Optional<ImageDimensions> imageDimensions() {
        return Optional.ofNullable(imageDimensions);
    }

    public Optional<AltText> altText() {
        return Optional.ofNullable(altText);
    }

    public DecorativeImageFlag decorative() {
        return decorative;
    }

    public ProcessingStatus processingStatus() {
        return processingStatus;
    }

    public List<AssetVariant> variants() {
        return variants;
    }

    public List<AssetValidationResult> validationResults() {
        return validationResults;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Version version() {
        return version;
    }
}
