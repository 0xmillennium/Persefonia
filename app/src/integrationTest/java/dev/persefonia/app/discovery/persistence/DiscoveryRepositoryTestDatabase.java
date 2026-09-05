package dev.persefonia.app.discovery.persistence;

import dev.persefonia.discovery.domain.DiscoverableResourceRepository;
import dev.persefonia.discovery.domain.RedirectRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import dev.persefonia.app.testsupport.SharedPostgresSpringIntegrationTest;

abstract class DiscoveryRepositoryTestDatabase extends SharedPostgresSpringIntegrationTest {
    @Autowired DiscoverableResourceRepository resources;
    @Autowired RedirectRuleRepository redirects;
    @Autowired JdbcTemplate jdbc;

}
