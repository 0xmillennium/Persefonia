package dev.persefonia.app.discovery.persistence;

import dev.persefonia.discovery.domain.DiscoverableResourceRepository;
import dev.persefonia.discovery.domain.RedirectRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
abstract class DiscoveryRepositoryTestDatabase {
    private static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.integrationDatabase();
    private static boolean migrated;

    @Autowired DiscoverableResourceRepository resources;
    @Autowired RedirectRuleRepository redirects;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void truncateDiscoveryTables() {
        migrateOnce();
        jdbc.execute("TRUNCATE discovery.redirect_rules, discovery.discoverable_resources");
    }

    private static synchronized void migrateOnce() {
        if (migrated) {
            return;
        }        migrated = true;
    }
}
