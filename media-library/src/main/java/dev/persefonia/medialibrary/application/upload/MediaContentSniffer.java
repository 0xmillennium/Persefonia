package dev.persefonia.medialibrary.application.upload;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class MediaContentSniffer {
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46, 0x2D};

    public DetectedMediaType detect(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        byte[] prefix = input.readNBytes(PNG_SIGNATURE.length);
        if (startsWith(prefix, JPEG_SIGNATURE)) {
            return DetectedMediaType.JPEG;
        }
        if (startsWith(prefix, PNG_SIGNATURE)) {
            return DetectedMediaType.PNG;
        }
        if (startsWith(prefix, PDF_SIGNATURE)) {
            return DetectedMediaType.PDF;
        }
        return DetectedMediaType.UNKNOWN;
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }
}
