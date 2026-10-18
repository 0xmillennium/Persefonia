package dev.persefonia.webpublic;

import dev.persefonia.medialibrary.application.publicview.PublicImageVariantView;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/** Publicly eligible cover candidates; the browser chooses pixels without changing composition. */
public record PublicHomeCoverView(List<PublicImageVariantView> variants) {
    public PublicHomeCoverView {
        variants = variants.stream().sorted(Comparator.comparingInt(PublicImageVariantView::width)).toList();
        if (variants.isEmpty()) throw new IllegalArgumentException("Cover requires a usable variant");
    }

    public PublicImageVariantView image() {
        return variants.stream().filter(image -> image.variantName().equals("medium"))
                .findFirst().orElse(variants.getLast());
    }

    public String sourceSet() {
        var widths = new LinkedHashMap<Integer, PublicImageVariantView>();
        variants.forEach(image -> widths.putIfAbsent(image.width(), image));
        return widths.values().stream().map(image -> image.url() + " " + image.width() + "w")
                .collect(Collectors.joining(", "));
    }
}
