package dev.persefonia.app.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.communication.application.authorization.ContactMessageCommandActor;
import dev.persefonia.communication.application.command.ContactMessageStatusCommandGateway;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusCommand;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusResult;
import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.domain.contact.ContactBody;
import dev.persefonia.communication.domain.contact.ContactMessage;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.communication.domain.contact.ContactSubject;
import dev.persefonia.communication.domain.contact.SenderEmail;
import dev.persefonia.communication.domain.contact.SenderName;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationException;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
class TransactionalContactMessageStatusCommandGatewayTest {
    private static final Instant NOW = Instant.parse("2026-09-02T08:00:00Z");
    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final ContactMessageCommandActor OWNER = new ContactMessageCommandActor(OWNER_ID, true, true);
    private static final PostgreSQLContainer POSTGRES = postgresContainer();
    private static boolean migrated;

    static {
        POSTGRES.start();
    }

    @Autowired ContactMessageStatusCommandGateway gateway;
    @Autowired ContactMessageRepository messages;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void resetDatabase() {
        migrateOnce();
        jdbc.execute("TRUNCATE communication.contact_messages CASCADE");
    }

    @Test
    void gatewayImplementsFrameworkFreeContractAndIsTransactionallyProxied() {
        assertThat(gateway).isInstanceOf(ContactMessageStatusCommandGateway.class);
        assertThat(AopUtils.isAopProxy(gateway)).isTrue();
        assertThat(AopUtils.getTargetClass(gateway)).isEqualTo(TransactionalContactMessageStatusCommandGateway.class);
    }

    @Test
    void normalGatewayCallCommitsCurrentStatusAndOneHistoryRow() {
        ContactMessage message = saveMessage("commit");

        UpdateContactMessageStatusResult result = gateway.update(command(message.id(), OWNER));

        assertThat(result).isEqualTo(new UpdateContactMessageStatusResult.Updated(
                message.id(), ContactMessageStatus.NEW, ContactMessageStatus.READ));
        assertThat(messages.findById(message.id()).orElseThrow().status()).isEqualTo(ContactMessageStatus.READ);
        assertThat(historyCount(message.id())).isEqualTo(1);
    }

    @Test
    void outerRollbackRestoresStatusAndRemovesHistoryWrittenByRepositoryTransactionTemplate() {
        ContactMessage message = saveMessage("rollback");

        transactions.executeWithoutResult(status -> {
            gateway.update(command(message.id(), OWNER));
            assertThat(messages.findById(message.id()).orElseThrow().status()).isEqualTo(ContactMessageStatus.READ);
            assertThat(historyCount(message.id())).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertThat(messages.findById(message.id()).orElseThrow().status()).isEqualTo(ContactMessageStatus.NEW);
        assertThat(historyCount(message.id())).isZero();
    }

    @Test
    void unauthorizedExecutionProducesNoSourceMutation() {
        ContactMessage message = saveMessage("unauthorized");
        ContactMessageCommandActor editor = new ContactMessageCommandActor(UUID.randomUUID(), true, false);

        assertThatThrownBy(() -> gateway.update(command(message.id(), editor)))
                .isInstanceOf(AdminCommandAuthorizationException.class);

        assertThat(messages.findById(message.id()).orElseThrow().status()).isEqualTo(ContactMessageStatus.NEW);
        assertThat(historyCount(message.id())).isZero();
    }

    private UpdateContactMessageStatusCommand command(
            ContactMessageId messageId,
            ContactMessageCommandActor actor) {
        return new UpdateContactMessageStatusCommand(actor, messageId, ContactMessageStatus.READ, NOW.plusSeconds(60));
    }

    private ContactMessage saveMessage(String key) {
        ContactMessage message = ContactMessage.create(
                ContactMessageId.from(UUID.nameUUIDFromBytes(("transaction-contact-" + key).getBytes())),
                SenderName.of("Ada " + key),
                SenderEmail.of("ada." + key + "@example.test"),
                ContactSubject.of("Transactional contact " + key),
                ContactBody.of("Body " + key),
                NOW);
        messages.save(message);
        return message;
    }

    private long historyCount(ContactMessageId messageId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM communication.contact_message_status_changes WHERE contact_message_id = ?",
                Long.class,
                messageId.value());
    }

    private static synchronized void migrateOnce() {
        if (migrated) {
            return;
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas("operations")
                .createSchemas(true)
                .load()
                .migrate();
        migrated = true;
    }

    private static PostgreSQLContainer postgresContainer() {
        PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
        postgres.withDatabaseName("persefonia_contact_command_gateway");
        postgres.withUsername("persefonia");
        postgres.withPassword("persefonia_dev");
        return postgres;
    }
}
