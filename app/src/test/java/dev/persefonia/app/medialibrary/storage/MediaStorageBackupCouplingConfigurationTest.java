package dev.persefonia.app.medialibrary.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MediaStorageBackupCouplingConfigurationTest {
    @Test
    void envExampleNamesMediaHostPathAndPostgresqlBackupCoupling() throws Exception {
        String envExample = Files.readString(envExamplePath());

        assertThat(envExample).contains("PERSEFONIA_MEDIA_HOST_PATH=./.runtime/media");
        assertThat(envExample).contains("Back up and restore it together with PostgreSQL");
    }

    private static Path envExamplePath() throws IOException {
        for (Path candidate : new Path[] {Path.of(".env.example"), Path.of("../.env.example")}) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IOException(".env.example was not found");
    }
}
