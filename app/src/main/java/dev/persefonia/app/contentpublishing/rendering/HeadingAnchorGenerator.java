package dev.persefonia.app.contentpublishing.rendering;

import dev.persefonia.contentpublishing.domain.content.HeadingAnchor;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class HeadingAnchorGenerator {
    List<HeadingAnchor> generateUniqueAnchors(List<String> headingTexts) {
        Map<String, Integer> occurrences = new HashMap<>();
        List<HeadingAnchor> anchors = new ArrayList<>(headingTexts.size());
        for (String headingText : headingTexts) {
            String base = normalize(headingText);
            int occurrence = occurrences.merge(base, 1, Integer::sum);
            anchors.add(HeadingAnchor.of(occurrence == 1 ? base : base + "-" + occurrence));
        }
        return List.copyOf(anchors);
    }

    private String normalize(String headingText) {
        String turkishNormalized = headingText
                .replace('ç', 'c').replace('Ç', 'c')
                .replace('ğ', 'g').replace('Ğ', 'g')
                .replace('ı', 'i').replace('I', 'i').replace('İ', 'i')
                .replace('ö', 'o').replace('Ö', 'o')
                .replace('ş', 's').replace('Ş', 's')
                .replace('ü', 'u').replace('Ü', 'u');
        String withoutDiacritics = Normalizer.normalize(turkishNormalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String anchor = withoutDiacritics.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return anchor.isEmpty() ? "section" : anchor;
    }
}
