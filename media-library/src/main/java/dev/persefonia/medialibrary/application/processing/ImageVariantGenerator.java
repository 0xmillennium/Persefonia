package dev.persefonia.medialibrary.application.processing;

import java.util.List;

public interface ImageVariantGenerator {
    List<GeneratedImageVariant> generate(ImageVariantGenerationRequest request);
}
