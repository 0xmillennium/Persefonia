package dev.persefonia.app.taxonomy.persistence;

import dev.persefonia.taxonomy.domain.port.TagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import dev.persefonia.app.testsupport.SharedPostgresSpringIntegrationTest;

abstract class TaxonomyRepositoryTestDatabase extends SharedPostgresSpringIntegrationTest {

    @Autowired TagRepository tags;
    @Autowired JdbcTemplate jdbc;

}
