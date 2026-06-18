package dev.persefonia.medialibrary.application.processing;

import dev.persefonia.medialibrary.domain.asset.VariantName;
import java.util.Objects;

public record ImageVariantSpec(VariantName name, int maximumWidth, int maximumHeight) {
    public ImageVariantSpec {
        Objects.requireNonNull(name, "name");
        if (maximumWidth <= 0 || maximumHeight <= 0) {
            throw new IllegalArgumentException("variant bounds must be positive");
        }
    }
}
