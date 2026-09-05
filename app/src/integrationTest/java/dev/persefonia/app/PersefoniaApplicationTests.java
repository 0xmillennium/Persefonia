package dev.persefonia.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

@SpringBootTest
@ActiveProfiles("test")
class PersefoniaApplicationTests {
    static final SharedPostgresTestServer.Database postgres = postgresContainer();

    private static SharedPostgresTestServer.Database postgresContainer() {
        SharedPostgresTestServer.Database container = SharedPostgresTestServer.integrationDatabase();
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
