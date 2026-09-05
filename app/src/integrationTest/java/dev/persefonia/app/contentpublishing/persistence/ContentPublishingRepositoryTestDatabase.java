package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.model.series.port.SeriesRepository;
import dev.persefonia.contentpublishing.domain.revision.port.ContentRevisionRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import dev.persefonia.app.testsupport.SharedPostgresSpringIntegrationTest;

abstract class ContentPublishingRepositoryTestDatabase extends SharedPostgresSpringIntegrationTest {

    @Autowired
    ContentItemRepository contentItems;

    @Autowired
    ContentRevisionRepository contentRevisions;

    @Autowired
    SeriesRepository seriesRepository;

    @Autowired
    NamedParameterJdbcTemplate namedJdbc;

    @Autowired
    JdbcTemplate jdbc;

}
