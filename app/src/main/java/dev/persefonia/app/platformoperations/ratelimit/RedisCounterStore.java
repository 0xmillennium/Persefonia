package dev.persefonia.app.platformoperations.ratelimit;

import java.time.Duration;

interface RedisCounterStore {
    long increment(String key);

    void expire(String key, Duration window);
}
