package dev.persefonia.app.medialibrary.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MediaStorageBackupCouplingConfigurationTest {
    @Test
    void envExampleNamesMediaStorageRootAndPostgresqlBackupCoupling() throws Exception {
        String envExample = Files.readString(envExamplePath());

        assertThat(envExample).contains("PERSEFONIA_MEDIA_STORAGE_REQUIRED=true");
        assertThat(envExample).contains("PERSEFONIA_MEDIA_STORAGE_ROOT=./var/persefonia-media");
        assertThat(envExample).contains("Back up and restore this directory together with PostgreSQL");
        assertThat(envExample).contains("Asset storage backup and PostgreSQL backup must be restored together");
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
