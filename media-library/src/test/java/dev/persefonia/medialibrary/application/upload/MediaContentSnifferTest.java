package dev.persefonia.medialibrary.application.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class MediaContentSnifferTest {
    private final MediaContentSniffer sniffer = new MediaContentSniffer();

    @Test
    void detectsSupportedSignaturesAndRejectsUnknownSignature() throws Exception {
        assertThat(detect(bytes(0xFF, 0xD8, 0xFF, 0x01))).isEqualTo(DetectedMediaType.JPEG);
        assertThat(detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
                .isEqualTo(DetectedMediaType.PNG);
        assertThat(detect("%PDF-1.7".getBytes())).isEqualTo(DetectedMediaType.PDF);
        assertThat(detect("<svg".getBytes())).isEqualTo(DetectedMediaType.UNKNOWN);
    }

    private DetectedMediaType detect(byte[] content) throws Exception {
        return sniffer.detect(new ByteArrayInputStream(content));
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }
        return bytes;
    }
}
