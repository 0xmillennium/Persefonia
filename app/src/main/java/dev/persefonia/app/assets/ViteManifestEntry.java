package dev.persefonia.app.assets;

import java.util.List;

public record ViteManifestEntry(String file, List<String> css) {
    public ViteManifestEntry {
        css = css == null ? List.of() : List.copyOf(css);
    }
}
