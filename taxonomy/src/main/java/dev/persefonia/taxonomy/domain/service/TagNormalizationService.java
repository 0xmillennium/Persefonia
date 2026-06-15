package dev.persefonia.taxonomy.domain.service;

import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.TagName;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import java.text.Normalizer;
import java.util.Locale;

public final class TagNormalizationService {
    public NormalizedTagName normalizeName(TagName name) {
        return NormalizedTagName.ofCanonical(name.value()
                .trim()
                .replaceAll("\\s+", " ")
                .replace('İ', 'I')
                .toLowerCase(Locale.ROOT));
    }

    public TagSlug generateSlug(TagName name) {
        return normalizeSlug(name.value());
    }

    public TagSlug normalizeSlug(String value) {
        if (value == null) {
            return TagSlug.ofCanonical("");
        }
        String turkishAscii = value
                .replace('ı', 'i').replace('İ', 'I')
                .replace('ş', 's').replace('Ş', 'S')
                .replace('ğ', 'g').replace('Ğ', 'G')
                .replace('ç', 'c').replace('Ç', 'C')
                .replace('ö', 'o').replace('Ö', 'O')
                .replace('ü', 'u').replace('Ü', 'U');
        String canonical = Normalizer.normalize(turkishAscii, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return TagSlug.ofCanonical(canonical);
    }
}
