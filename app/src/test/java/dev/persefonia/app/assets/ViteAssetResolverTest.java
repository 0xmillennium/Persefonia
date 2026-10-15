package dev.persefonia.app.assets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    @Test
    void includesTransitiveStaticStylesInOrderWithoutDuplicatesOrDynamicImports() {
        var manifest = new ByteArrayResource("""
                {
                  "src/main.ts": {"file":"main.js","css":["main.css"],"imports":["_shared.js","_other.js"],"dynamicImports":["_lazy.js"]},
                  "_shared.js": {"file":"shared.js","css":["shared.css"],"imports":["_base.js"]},
                  "_other.js": {"file":"other.js","css":["shared.css"]},
                  "_base.js": {"file":"base.js","css":["base.css"],"imports":["_shared.js"]},
                  "_lazy.js": {"file":"lazy.js","css":["lazy.css"]}
                }
                """.getBytes(StandardCharsets.UTF_8));
        var resolver = new ViteAssetResolver(new ObjectMapper(), manifest);
        assertEquals(List.of("/assets/base.css", "/assets/shared.css", "/assets/main.css"),
                resolver.stylesheetPaths(ENTRY));
    }

    @Test
    void rejectsMissingStaticImportsInsteadOfDeliveringAnUnstyledPage() {
        var manifest = new ByteArrayResource("""
                {"src/main.ts":{"file":"main.js","imports":["_missing.js"]}}
                """.getBytes(StandardCharsets.UTF_8));
        var resolver = new ViteAssetResolver(new ObjectMapper(), manifest);
        assertThrows(IllegalArgumentException.class, () -> resolver.stylesheetPaths(ENTRY));
    }
}
