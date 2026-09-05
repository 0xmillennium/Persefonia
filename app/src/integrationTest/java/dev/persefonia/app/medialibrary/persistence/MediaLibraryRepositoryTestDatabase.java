package dev.persefonia.app.medialibrary.persistence;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import dev.persefonia.app.testsupport.SharedPostgresSpringIntegrationTest;

abstract class MediaLibraryRepositoryTestDatabase extends SharedPostgresSpringIntegrationTest {

    @Autowired AssetRepository assets;
    @Autowired JdbcTemplate jdbc;

}
