package dev.persefonia.medialibrary.application.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class UploadValidationPolicyTest {
    private final UploadValidationPolicy policy = new UploadValidationPolicy(10, 12);

    @Test
    void extensionMappingsAcceptOnlyTheSupportedTypeExtensions() {
        assertThat(AllowedMediaType.JPEG.acceptsExtension("jpg")).isTrue();
        assertThat(AllowedMediaType.JPEG.acceptsExtension(".JPEG")).isTrue();
        assertThat(AllowedMediaType.PNG.acceptsExtension("png")).isTrue();
        assertThat(AllowedMediaType.PDF.acceptsExtension("pdf")).isTrue();
        assertThat(AllowedMediaType.PNG.acceptsExtension("jpg")).isFalse();
    }

    @Test
    void rejectsUnsupportedDeclaredTypes() {
        for (String[] unsupported : List.of(
                new String[] {"image/svg+xml", "svg"},
                new String[] {"image/webp", "webp"},
                new String[] {"image/gif", "gif"},
                new String[] {
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"
                })) {
            var result = policy.validateDeclared(command(unsupported[0], unsupported[1], 4));
            assertThat(result.errors())
                    .extracting(UploadValidationError::code)
                    .contains(UploadValidationErrorCode.CONTENT_TYPE_NOT_ALLOWED);
        }
    }

    @Test
    void rejectsContentTypeExtensionMismatch() {
        var result = policy.validateDeclared(command("image/jpeg", "png", 4));

        assertThat(result.errors())
                .extracting(UploadValidationError::code)
                .contains(UploadValidationErrorCode.CONTENT_TYPE_EXTENSION_MISMATCH);
    }

    @Test
    void rejectsOversizedImageAndPdf() {
        assertThat(policy.validateDeclared(command("image/jpeg", "jpg", 11)).errors())
                .extracting(UploadValidationError::code)
                .contains(UploadValidationErrorCode.FILE_TOO_LARGE);
        assertThat(policy.validateDeclared(command("application/pdf", "pdf", 13)).errors())
                .extracting(UploadValidationError::code)
                .contains(UploadValidationErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void rejectsDeclaredSizeMismatchAndMagicMismatch() {
        UploadAssetCommand command = command("image/png", "png", 8);

        assertThat(policy.validateStaged(command, AllowedMediaType.PNG, DetectedMediaType.PNG, 7))
                .extracting(UploadValidationError::code)
                .contains(UploadValidationErrorCode.DECLARED_SIZE_MISMATCH);
        assertThat(policy.validateStaged(command, AllowedMediaType.PNG, DetectedMediaType.JPEG, 8))
                .extracting(UploadValidationError::code)
                .contains(UploadValidationErrorCode.MAGIC_BYTES_MISMATCH);
    }

    private static UploadAssetCommand command(String contentType, String extension, long size) {
        return new UploadAssetCommand(
                "upload." + extension,
                contentType,
                extension,
                size,
                () -> new ByteArrayInputStream(new byte[(int) size]));
    }
}
