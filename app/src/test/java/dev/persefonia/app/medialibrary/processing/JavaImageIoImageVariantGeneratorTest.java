package dev.persefonia.app.medialibrary.processing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.medialibrary.application.processing.ImageVariantGenerationRequest;
import dev.persefonia.medialibrary.application.processing.ImageVariantSpecs;
import dev.persefonia.medialibrary.application.upload.ChecksumCalculator;
import dev.persefonia.medialibrary.domain.asset.ContentTypeName;
import dev.persefonia.medialibrary.domain.asset.VariantName;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class JavaImageIoImageVariantGeneratorTest {
    private final JavaImageIoImageVariantGenerator generator = new JavaImageIoImageVariantGenerator();

    @Test
    void generatesAllFitWithinJpegVariantsWithoutUpscaling() throws Exception {
        byte[] original = JavaImageIoImageMetadataReaderTest.imageBytes(
                "jpeg", java.awt.image.BufferedImage.TYPE_INT_RGB, 2000, 1000);
        var variants = generator.generate(new ImageVariantGenerationRequest(
                original, ContentTypeName.of("image/jpeg"), ImageVariantSpecs.all()));
        Map<VariantName, dev.persefonia.medialibrary.application.processing.GeneratedImageVariant> byName =
                variants.stream().collect(Collectors.toMap(value -> value.name(), value -> value));

        assertThat(byName).containsOnlyKeys(
                VariantName.THUMBNAIL, VariantName.MEDIUM, VariantName.LARGE, VariantName.OG);
        assertThat(byName.get(VariantName.THUMBNAIL).width().value()).isEqualTo(320);
        assertThat(byName.get(VariantName.THUMBNAIL).height().value()).isEqualTo(160);
        assertThat(byName.get(VariantName.OG).width().value()).isEqualTo(1200);
        assertThat(byName.get(VariantName.OG).height().value()).isEqualTo(600);
        assertThat(variants).allSatisfy(variant -> {
            assertThat(variant.contentType().value()).isEqualTo("image/jpeg");
            assertThat(variant.fileExtension().value()).isEqualTo("jpg");
        });
    }

    @Test
    void pngPreservesFormatAndSmallImageIsNotUpscaledWithStableBytes() throws Exception {
        byte[] original = JavaImageIoImageMetadataReaderTest.imageBytes(
                "png", java.awt.image.BufferedImage.TYPE_INT_ARGB, 40, 20);
        ImageVariantGenerationRequest request = new ImageVariantGenerationRequest(
                original, ContentTypeName.of("image/png"), ImageVariantSpecs.all());
        var first = generator.generate(request);
        var second = generator.generate(request);

        assertThat(first).allSatisfy(variant -> {
            assertThat(variant.width().value()).isEqualTo(40);
            assertThat(variant.height().value()).isEqualTo(20);
            assertThat(variant.contentType().value()).isEqualTo("image/png");
            assertThat(ImageIO.read(new ByteArrayInputStream(variant.bytes()))).isNotNull();
        });
        ChecksumCalculator checksums = new ChecksumCalculator();
        for (int index = 0; index < first.size(); index++) {
            assertThat(checksums.calculate(new ByteArrayInputStream(first.get(index).bytes())))
                    .isEqualTo(checksums.calculate(new ByteArrayInputStream(second.get(index).bytes())));
        }
    }
}
