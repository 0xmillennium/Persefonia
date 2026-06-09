package dev.persefonia.app.assets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.ObjectMapper;

class ViteAssetResolverTest {
    private static final String ENTRY = "src/main.ts";

    private final ClassPathResource manifestResource =
            new ClassPathResource(ViteAssetResolver.MANIFEST_CLASSPATH_LOCATION);
    private final ViteAssetResolver resolver = new ViteAssetResolver(new ObjectMapper(), manifestResource);

    @Test
    void resolvesHashedScriptAndStylesheetsFromClasspathManifest() {
        assertTrue(manifestResource.exists());

        String scriptPath = resolver.scriptPath(ENTRY);
        assertTrue(scriptPath.startsWith("/assets/"));
        assertTrue(scriptPath.endsWith(".js"));
        assertFalse(scriptPath.contains(ENTRY));

        var stylesheetPaths = resolver.stylesheetPaths(ENTRY);
        assertFalse(stylesheetPaths.isEmpty());
        assertTrue(stylesheetPaths.stream().allMatch(path -> path.startsWith("/assets/")));
        assertTrue(stylesheetPaths.stream().allMatch(path -> path.endsWith(".css")));
    }
}
