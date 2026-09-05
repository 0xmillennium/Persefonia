package dev.persefonia.app.medialibrary.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MediaStorageProperties.class)
@ConditionalOnProperty(prefix = "persefonia.media", name = "storage-required", havingValue = "true")
class MediaStorageRequiredConfiguration {
    @Bean
    MediaStorageRequiredGuard mediaStorageRequiredGuard(
            MediaStorageProperties properties, ObjectProvider<MediaStorageReadinessService> readiness) {
        java.nio.file.Path storageRoot = properties.requireStorageRootPath();
        if (readiness.getIfAvailable() == null) {
            throw new IllegalStateException("persefonia.media.storage-root must be configured.");
        }
        return new MediaStorageRequiredGuard(storageRoot);
    }

    static final class MediaStorageRequiredGuard {
        private final java.nio.file.Path storageRoot;

        private MediaStorageRequiredGuard(java.nio.file.Path storageRoot) {
            this.storageRoot = storageRoot;
        }

        Path storageRoot() {
            return storageRoot;
        }
    }
}
