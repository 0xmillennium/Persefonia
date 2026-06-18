package dev.persefonia.app.medialibrary.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.medialibrary.application.processing.ImageProcessingException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class JavaImageIoImageMetadataReaderTest {
    @Test
    void readsJpegAndPngDimensions() throws Exception {
        JavaImageIoImageMetadataReader reader = new JavaImageIoImageMetadataReader(1_000_000);

        assertThat(reader.read(imageBytes("jpeg", BufferedImage.TYPE_INT_RGB, 640, 480))
                .dimensions().width().value()).isEqualTo(640);
        assertThat(reader.read(imageBytes("png", BufferedImage.TYPE_INT_ARGB, 321, 123))
                .dimensions().height().value()).isEqualTo(123);
    }

    @Test
    void invalidBytesAndOversizedPixelCountFail() throws Exception {
        assertThatThrownBy(() -> new JavaImageIoImageMetadataReader(100)
                .read("not-image".getBytes()))
                .isInstanceOf(ImageProcessingException.class);
        assertThatThrownBy(() -> new JavaImageIoImageMetadataReader(99)
                .read(imageBytes("png", BufferedImage.TYPE_INT_ARGB, 10, 10)))
                .isInstanceOf(ImageProcessingException.class)
                .hasMessageContaining("pixel limit");
    }

    static byte[] imageBytes(String format, int type, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, type);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
