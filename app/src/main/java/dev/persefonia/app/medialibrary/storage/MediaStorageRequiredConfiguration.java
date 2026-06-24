package dev.persefonia.app.medialibrary.storage;

import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MediaStorageProperties.class)
@ConditionalOnProperty(prefix = "persefonia.media", name = "storage-required", havingValue = "true")
class MediaStorageRequiredConfiguration {
    @Bean
    MediaStorageRequiredGuard mediaStorageRequiredGuard(MediaStorageProperties properties) {
        Path storageRoot = properties.requireStorageRootPath();
        new MediaStorageReadinessService(storageRoot).verifyReady();
        return new MediaStorageRequiredGuard(storageRoot);
    }

    static final class MediaStorageRequiredGuard {
        private final Path storageRoot;

        private MediaStorageRequiredGuard(Path storageRoot) {
            this.storageRoot = storageRoot;
        }

        Path storageRoot() {
            return storageRoot;
        }
    }
}
