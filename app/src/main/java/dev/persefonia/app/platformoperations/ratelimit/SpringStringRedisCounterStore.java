package dev.persefonia.app.platformoperations.ratelimit;

import java.time.Duration;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;

final class SpringStringRedisCounterStore implements RedisCounterStore {
    private final StringRedisTemplate redis;

    SpringStringRedisCounterStore(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
    }

    @Override
    public long increment(String key) {
        Long value = redis.opsForValue().increment(key);
        if (value == null) {
            throw new IllegalStateException("Redis increment returned no value");
        }
        return value;
    }

    @Override
    public void expire(String key, Duration window) {
        redis.expire(key, window);
    }
}
