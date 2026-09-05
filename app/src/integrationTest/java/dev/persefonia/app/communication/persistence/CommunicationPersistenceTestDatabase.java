package dev.persefonia.app.communication.persistence;

import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.application.query.ContactMessageAdminQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import dev.persefonia.app.testsupport.SharedPostgresSpringIntegrationTest;

abstract class CommunicationPersistenceTestDatabase extends SharedPostgresSpringIntegrationTest {

    @Autowired ContactMessageRepository contactMessages;
    @Autowired ContactMessageAdminQueryService adminQuery;
    @Autowired JdbcTemplate jdbc;

}
