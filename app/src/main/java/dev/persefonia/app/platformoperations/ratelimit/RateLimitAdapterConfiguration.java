package dev.persefonia.app.platformoperations.ratelimit;

import dev.persefonia.platformoperations.application.port.RateLimitPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ContactRateLimitProperties.class)
class RateLimitAdapterConfiguration {
    @Bean
    ContactRateLimitKeyFactory contactRateLimitKeyFactory(ContactRateLimitProperties properties) {
        return new ContactRateLimitKeyFactory(properties.secret());
    }

    @Bean
    RedisCounterStore redisCounterStore(StringRedisTemplate redis) {
        return new SpringStringRedisCounterStore(redis);
    }

    @Bean
    RateLimitPort rateLimitPort(RedisCounterStore counters, ContactRateLimitProperties properties) {
        return new RedisRateLimitPortAdapter(counters, properties.redisKeyPrefix());
    }
}
