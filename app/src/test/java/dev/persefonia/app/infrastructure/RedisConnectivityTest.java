package dev.persefonia.app.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

class RedisConnectivityTest {
    private static final int REDIS_PORT = 6379;

    @Test
    void respondsToPing() {
        try (GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine")
                .withCommand("redis-server", "--save", "", "--appendonly", "no")
                .withExposedPorts(REDIS_PORT)) {
            redis.start();

            RedisClient client = RedisClient.create(
                    "redis://" + redis.getHost() + ":" + redis.getMappedPort(REDIS_PORT));
            try (StatefulRedisConnection<String, String> connection = client.connect()) {
                assertEquals("PONG", connection.sync().ping());
            } finally {
                client.shutdown();
            }
        }
    }
}
