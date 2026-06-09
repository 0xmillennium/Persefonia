package dev.persefonia.app.assets;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;

import dev.persefonia.webpublic.FrontendAssetResolver;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class ViteAssetResolver implements FrontendAssetResolver {
    public static final String MANIFEST_CLASSPATH_LOCATION = "static/assets/.vite/manifest.json";

    private static final String ASSET_URL_PREFIX = "/assets/";

    private final ViteManifest manifest;

    public ViteAssetResolver(ObjectMapper objectMapper, Resource manifestResource) {
        this.manifest = readManifest(objectMapper, manifestResource);
    }

    @Override
    public String scriptPath(String entry) {
        return assetPath(manifest.requiredEntry(entry).file());
    }

    @Override
    public List<String> stylesheetPaths(String entry) {
        return manifest.requiredEntry(entry).css().stream()
                .map(ViteAssetResolver::assetPath)
                .toList();
    }

    private static ViteManifest readManifest(ObjectMapper objectMapper, Resource manifestResource) {
        try (InputStream input = manifestResource.getInputStream()) {
            Map<String, ViteManifestEntry> entries = objectMapper.readValue(input, new TypeReference<>() {
            });
            return new ViteManifest(entries);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to read Vite manifest from " + manifestResource.getDescription(),
                    exception);
        }
    }

    private static String assetPath(String file) {
        if (file == null || file.isBlank()) {
            throw new IllegalStateException("Vite manifest contains an empty asset filename");
        }
        return ASSET_URL_PREFIX + file;
    }
}
