package dev.persefonia.app.assets;

import java.util.Map;

public record ViteManifest(Map<String, ViteManifestEntry> entries) {
    public ViteManifest {
        entries = Map.copyOf(entries);
    }

    public ViteManifestEntry requiredEntry(String name) {
        ViteManifestEntry entry = entries.get(name);
        if (entry == null) {
            throw new IllegalArgumentException("Vite manifest entry not found: " + name);
        }
        return entry;
    }
}
