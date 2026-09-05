package dev.persefonia.app.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Common Spring context identity for integration tests using the shared application database. */
@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
public abstract class SharedPostgresSpringIntegrationTest {
    protected static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.integrationDatabase();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
