package dev.persefonia.app.medialibrary.processing;

import dev.persefonia.medialibrary.application.processing.GeneratedImageVariant;
import dev.persefonia.medialibrary.application.processing.ImageProcessingException;
import dev.persefonia.medialibrary.application.processing.ImageVariantGenerationRequest;
import dev.persefonia.medialibrary.application.processing.ImageVariantGenerator;
import dev.persefonia.medialibrary.application.processing.ImageVariantSpec;
import dev.persefonia.medialibrary.domain.asset.ContentTypeName;
import dev.persefonia.medialibrary.domain.asset.FileExtension;
import dev.persefonia.medialibrary.domain.asset.PixelHeight;
import dev.persefonia.medialibrary.domain.asset.PixelWidth;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public final class JavaImageIoImageVariantGenerator implements ImageVariantGenerator {
    @Override
    public List<GeneratedImageVariant> generate(ImageVariantGenerationRequest request) {
        OutputFormat format = OutputFormat.from(request.originalContentType().value());
        BufferedImage original = decode(request.originalBytes());
        List<GeneratedImageVariant> variants = new ArrayList<>();
        for (ImageVariantSpec spec : request.specs()) {
            int[] dimensions = fitWithin(
                    original.getWidth(), original.getHeight(), spec.maximumWidth(), spec.maximumHeight());
            BufferedImage scaled = scale(original, dimensions[0], dimensions[1], format.alpha());
            variants.add(new GeneratedImageVariant(
                    spec.name(),
                    PixelWidth.of(dimensions[0]),
                    PixelHeight.of(dimensions[1]),
                    ContentTypeName.of(format.contentType()),
                    FileExtension.of(format.extension()),
                    encode(scaled, format.writerFormat())));
        }
        return List.copyOf(variants);
    }

    private static BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new ImageProcessingException("Image bytes could not be decoded.");
            }
            return image;
        } catch (IOException exception) {
            throw new ImageProcessingException("Image bytes could not be decoded.", exception);
        }
    }

    private static int[] fitWithin(int width, int height, int maximumWidth, int maximumHeight) {
        double scale = Math.min(1.0, Math.min(
                (double) maximumWidth / width,
                (double) maximumHeight / height));
        return new int[] {
                Math.max(1, (int) Math.floor(width * scale)),
                Math.max(1, (int) Math.floor(height * scale))
        };
    }

    private static BufferedImage scale(BufferedImage original, int width, int height, boolean alpha) {
        int imageType = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage output = new BufferedImage(width, height, imageType);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private static byte[] encode(BufferedImage image, String writerFormat) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, writerFormat, output)) {
                throw new ImageProcessingException("No image writer is available for generated variants.");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ImageProcessingException("Generated image variant could not be encoded.", exception);
        }
    }

    private record OutputFormat(String contentType, String extension, String writerFormat, boolean alpha) {
        private static OutputFormat from(String contentType) {
            return switch (contentType) {
                case "image/jpeg" -> new OutputFormat("image/jpeg", "jpg", "jpeg", false);
                case "image/png" -> new OutputFormat("image/png", "png", "png", true);
                default -> throw new ImageProcessingException("Unsupported image content type.");
            };
        }
    }
}
