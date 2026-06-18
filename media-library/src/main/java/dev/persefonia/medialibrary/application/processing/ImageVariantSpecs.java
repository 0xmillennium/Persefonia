package dev.persefonia.medialibrary.application.processing;

import dev.persefonia.medialibrary.domain.asset.VariantName;
import java.util.List;

public final class ImageVariantSpecs {
    public static final ImageVariantSpec THUMBNAIL =
            new ImageVariantSpec(VariantName.THUMBNAIL, 320, 320);
    public static final ImageVariantSpec MEDIUM =
            new ImageVariantSpec(VariantName.MEDIUM, 960, 960);
    public static final ImageVariantSpec LARGE =
            new ImageVariantSpec(VariantName.LARGE, 1600, 1600);
    public static final ImageVariantSpec OG =
            new ImageVariantSpec(VariantName.OG, 1200, 630);
    private static final List<ImageVariantSpec> ALL = List.of(THUMBNAIL, MEDIUM, LARGE, OG);

    private ImageVariantSpecs() {
    }

    public static List<ImageVariantSpec> all() {
        return ALL;
    }
}
