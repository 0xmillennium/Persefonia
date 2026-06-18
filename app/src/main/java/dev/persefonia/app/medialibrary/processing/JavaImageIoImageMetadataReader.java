package dev.persefonia.app.medialibrary.processing;

import dev.persefonia.medialibrary.application.processing.ImageMetadata;
import dev.persefonia.medialibrary.application.processing.ImageMetadataReader;
import dev.persefonia.medialibrary.application.processing.ImageProcessingException;
import dev.persefonia.medialibrary.domain.asset.ImageDimensions;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class JavaImageIoImageMetadataReader implements ImageMetadataReader {
    private final long maximumPixels;

    public JavaImageIoImageMetadataReader(long maximumPixels) {
        if (maximumPixels <= 0) {
            throw new IllegalArgumentException("maximumPixels must be positive");
        }
        this.maximumPixels = maximumPixels;
    }

    @Override
    public ImageMetadata read(byte[] imageBytes) {
        int[] dimensions = readDimensions(imageBytes);
        if ((long) dimensions[0] * dimensions[1] > maximumPixels) {
            throw new ImageProcessingException("Decoded image exceeds the configured pixel limit.");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new ImageProcessingException("Image bytes could not be decoded.");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            if (width <= 0 || height <= 0) {
                throw new ImageProcessingException("Decoded image dimensions are invalid.");
            }
            return new ImageMetadata(ImageDimensions.of(width, height));
        } catch (IOException exception) {
            throw new ImageProcessingException("Image bytes could not be decoded.", exception);
        }
    }

    private static int[] readDimensions(byte[] imageBytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            if (input == null) {
                throw new ImageProcessingException("Image bytes could not be decoded.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ImageProcessingException("Image bytes could not be decoded.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new ImageProcessingException("Decoded image dimensions are invalid.");
                }
                return new int[] {width, height};
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new ImageProcessingException("Image bytes could not be decoded.", exception);
        }
    }
}
