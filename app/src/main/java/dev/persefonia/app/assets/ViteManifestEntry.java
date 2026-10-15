package dev.persefonia.app.assets;

import java.util.List;

public record ViteManifestEntry(String file, List<String> css, List<String> imports) {
    public ViteManifestEntry {
        css = css == null ? List.of() : List.copyOf(css);
        imports = imports == null ? List.of() : List.copyOf(imports);
    }
}
