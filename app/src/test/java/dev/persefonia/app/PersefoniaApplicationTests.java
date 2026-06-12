package dev.persefonia.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class PersefoniaApplicationTests {
    @Container
    static final PostgreSQLContainer postgres = postgresContainer();

    private static PostgreSQLContainer postgresContainer() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17-alpine");
        container.withDatabaseName("persefonia_test");
        container.withUsername("persefonia");
        container.withPassword("persefonia_dev");
        return container;
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void contextLoads() {
    }
}
