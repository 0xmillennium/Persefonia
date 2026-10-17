package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FrontendBuildConfigurationArchitectureTest {
    @Test
    void viteUsesCodeSplittingAndRetainsMainAndMermaidEntries() throws IOException {
        String config = Files.readString(Path.of("../frontend/vite.config.ts")).replace('\'', '"');

        assertThat(config)
                .doesNotContain("advancedChunks")
                .contains("codeSplitting")
                .contains("main: \"src/main.ts\"")
                .contains("\"mermaid-loader\": \"src/mermaid-loader.ts\"");
    }

    @Test
    void mermaidRemainsOutsideMainEntry() throws IOException {
        String main = Files.readString(Path.of("../frontend/src/main.ts")).replace('\'', '"');
        String mermaidLoader = Files.readString(Path.of("../frontend/src/mermaid-loader.ts")).replace('\'', '"');

        assertThat(main).doesNotContain("from \"mermaid\"");
        assertThat(mermaidLoader).contains("from \"mermaid\"");
    }
}
