package dev.persefonia.app.platformoperations.cache;

import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequestPort;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequestService;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchRepository;
import dev.persefonia.platformoperations.application.operations.CacheInvalidationOperationsQueryPort;
import dev.persefonia.platformoperations.application.operations.CacheInvalidationOperationsQueryService;
import dev.persefonia.platformoperations.application.operations.CacheInvalidationOperationsReadPort;
import dev.persefonia.platformoperations.application.operations.CacheInvalidationRecoveryPolicy;
import dev.persefonia.app.platformoperations.cache.config.CachePurgeProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CacheInvalidationApplicationConfiguration {
    @Bean
    CacheInvalidationRequestPort cacheInvalidationRequestPort(
            CacheInvalidationBatchRepository batches, Clock clock) {
        return new CacheInvalidationRequestService(batches, clock);
    }

    @Bean
    CacheInvalidationRecoveryPolicy cacheInvalidationRecoveryPolicy(CachePurgeProperties properties) {
        return new CacheInvalidationRecoveryPolicy(properties.getStrandedAfter());
    }

    @Bean
    CacheInvalidationOperationsQueryPort cacheInvalidationOperationsQueryPort(
            CacheInvalidationOperationsReadPort reads, Clock clock) {
        return new CacheInvalidationOperationsQueryService(reads, clock);
    }
}
