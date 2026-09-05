package dev.persefonia.app.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

class PostCommitTaskExecutorTest {
    private static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.integrationDatabase();
    private final PostCommitTaskExecutor executor = new SpringTransactionSynchronizationPostCommitTaskExecutor();
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;

    @BeforeAll
    static void startDatabase() {    }

    @AfterAll
    static void stopDatabase() {    }

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS post_commit_probe");
        jdbc.execute("CREATE TABLE post_commit_probe (id int PRIMARY KEY, marker text NOT NULL)");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void afterCommitRunsAfterSuccessfulCommit() {
        AtomicBoolean ran = new AtomicBoolean(false);

        transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO post_commit_probe (id, marker) VALUES (1, 'committed')");
            executor.afterCommit(() -> ran.set(countRows() == 1));
            assertThat(ran).isFalse();
        });

        assertThat(ran).isTrue();
    }

    @Test
    void afterCommitDoesNotRunAfterRollback() {
        AtomicBoolean ran = new AtomicBoolean(false);

        transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO post_commit_probe (id, marker) VALUES (1, 'rolled-back')");
            executor.afterCommit(() -> ran.set(true));
            status.setRollbackOnly();
        });

        assertThat(ran).isFalse();
        assertThat(countRows()).isZero();
    }

    @Test
    void afterCommitExceptionDoesNotRollbackCommittedTransaction() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO post_commit_probe (id, marker) VALUES (1, 'committed')");
            executor.afterCommit(() -> {
                throw new IllegalStateException("mail task failed after commit");
            });
        })).isInstanceOf(IllegalStateException.class);

        assertThat(countRows()).isEqualTo(1);
    }

    @Test
    void outsideTransactionRunsImmediately() {
        AtomicBoolean ran = new AtomicBoolean(false);

        executor.afterCommit(() -> ran.set(true));

        assertThat(ran).isTrue();
    }

    private int countRows() {
        return jdbc.queryForObject("SELECT count(*) FROM post_commit_probe", Integer.class);
    }
}
