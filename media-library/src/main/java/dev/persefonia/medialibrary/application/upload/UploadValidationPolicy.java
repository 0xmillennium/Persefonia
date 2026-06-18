package dev.persefonia.medialibrary.application.upload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class UploadValidationPolicy {
    public static final long DEFAULT_MAX_IMAGE_BYTES = 10_485_760L;
    public static final long DEFAULT_MAX_PDF_BYTES = 10_485_760L;

    private final long maxImageBytes;
    private final long maxPdfBytes;

    public UploadValidationPolicy(long maxImageBytes, long maxPdfBytes) {
        if (maxImageBytes <= 0 || maxPdfBytes <= 0) {
            throw new IllegalArgumentException("upload size limits must be positive");
        }
        this.maxImageBytes = maxImageBytes;
        this.maxPdfBytes = maxPdfBytes;
    }

    public DeclaredValidation validateDeclared(UploadAssetCommand command) {
        Objects.requireNonNull(command, "command");
        List<UploadValidationError> errors = new ArrayList<>();
        if (isBlank(command.originalFilename())) {
            errors.add(error(
                    UploadValidationErrorCode.ORIGINAL_FILENAME_REQUIRED,
                    "An original filename is required."));
        }
        if (command.declaredSize() <= 0) {
            errors.add(error(
                    UploadValidationErrorCode.DECLARED_SIZE_REQUIRED,
                    "A positive declared file size is required."));
        }
        if (isBlank(command.declaredContentType())) {
            errors.add(error(
                    UploadValidationErrorCode.CONTENT_TYPE_REQUIRED,
                    "A declared content type is required."));
        }
        if (isBlank(command.declaredExtension())) {
            errors.add(error(
                    UploadValidationErrorCode.FILE_EXTENSION_REQUIRED,
                    "A declared file extension is required."));
        }

        Optional<AllowedMediaType> allowedType =
                AllowedMediaType.fromContentType(command.declaredContentType());
        if (!isBlank(command.declaredContentType()) && allowedType.isEmpty()) {
            errors.add(error(
                    UploadValidationErrorCode.CONTENT_TYPE_NOT_ALLOWED,
                    "The declared content type is not allowed."));
        }
        if (!isBlank(command.declaredExtension()) && !isKnownExtension(command.declaredExtension())) {
            errors.add(error(
                    UploadValidationErrorCode.FILE_EXTENSION_NOT_ALLOWED,
                    "The declared file extension is not allowed."));
        }
        if (allowedType.isPresent()
                && !isBlank(command.declaredExtension())
                && isKnownExtension(command.declaredExtension())
                && !allowedType.get().acceptsExtension(command.declaredExtension())) {
            errors.add(error(
                    UploadValidationErrorCode.CONTENT_TYPE_EXTENSION_MISMATCH,
                    "The declared content type and file extension do not match."));
        }
        if (allowedType.isPresent()
                && command.declaredSize() > maximumBytes(allowedType.get())) {
            errors.add(error(
                    UploadValidationErrorCode.FILE_TOO_LARGE,
                    "The declared file size exceeds the allowed limit."));
        }
        return new DeclaredValidation(allowedType.orElse(null), errors);
    }

    public List<UploadValidationError> validateStaged(
            UploadAssetCommand command,
            AllowedMediaType declaredType,
            DetectedMediaType detectedType,
            long actualSize) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(declaredType, "declaredType");
        Objects.requireNonNull(detectedType, "detectedType");
        List<UploadValidationError> errors = new ArrayList<>();
        if (actualSize <= 0) {
            errors.add(error(UploadValidationErrorCode.FILE_EMPTY, "The uploaded file is empty."));
        }
        if (actualSize > maximumBytes(declaredType)) {
            errors.add(error(
                    UploadValidationErrorCode.FILE_TOO_LARGE,
                    "The uploaded file exceeds the allowed size limit."));
        }
        if (actualSize != command.declaredSize()) {
            errors.add(error(
                    UploadValidationErrorCode.DECLARED_SIZE_MISMATCH,
                    "The uploaded byte count does not match the declared size."));
        }
        if (detectedType.allowedMediaType().isEmpty()
                || detectedType.allowedMediaType().get() != declaredType) {
            errors.add(error(
                    UploadValidationErrorCode.MAGIC_BYTES_MISMATCH,
                    "The uploaded content does not match the declared media type."));
        }
        return List.copyOf(errors);
    }

    public long maximumBytes(AllowedMediaType mediaType) {
        return mediaType.assetKind() == dev.persefonia.medialibrary.domain.asset.AssetKind.IMAGE
                ? maxImageBytes
                : maxPdfBytes;
    }

    private static boolean isKnownExtension(String extension) {
        for (AllowedMediaType mediaType : AllowedMediaType.values()) {
            if (mediaType.acceptsExtension(extension)) {
                return true;
            }
        }
        return false;
    }

    private static UploadValidationError error(UploadValidationErrorCode code, String message) {
        return new UploadValidationError(code, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record DeclaredValidation(AllowedMediaType allowedMediaType, List<UploadValidationError> errors) {
        public DeclaredValidation {
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        }

        public Optional<AllowedMediaType> allowedMediaTypeOptional() {
            return Optional.ofNullable(allowedMediaType);
        }

        public boolean accepted() {
            return errors.isEmpty() && allowedMediaType != null;
        }
    }
}
