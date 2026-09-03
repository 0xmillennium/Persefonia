package dev.persefonia.app.platformoperations.cache;

import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequestPort;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequestService;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchRepository;
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
}
