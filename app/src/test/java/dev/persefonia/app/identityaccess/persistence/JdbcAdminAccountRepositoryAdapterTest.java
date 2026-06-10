package dev.persefonia.app.identityaccess.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import dev.persefonia.identityaccess.domain.admin.AdminAccount;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.NormalizedEmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;
import dev.persefonia.identityaccess.domain.admin.Version;

class JdbcAdminAccountRepositoryAdapterTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final Instant CREATED_AT = Instant.parse("2026-06-11T08:00:00Z");
    private static JdbcTemplate jdbc;
    private static JdbcAdminAccountRepositoryAdapter repository;

    @BeforeAll
    static void migrateDatabase() {
        POSTGRES.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas("operations")
                .createSchemas(true)
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        repository = new JdbcAdminAccountRepositoryAdapter(new NamedParameterJdbcTemplate(dataSource));
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void cleanIamTables() {
        jdbc.update("DELETE FROM iam.admin_account_roles");
        jdbc.update("DELETE FROM iam.admin_accounts");
    }

    @Test
    void saveInsertsAccountAndRoles() {
        AdminAccount account = account("subject", "owner@example.com", Set.of(AdminRole.OWNER, AdminRole.EDITOR));

        repository.save(account);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM iam.admin_accounts", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForList(
                "SELECT role FROM iam.admin_account_roles ORDER BY role",
                String.class)).containsExactly("EDITOR", "OWNER");
    }

    @Test
    void findByIdReturnsRehydratedAccount() {
        AdminAccount account = account("subject", "owner@example.com", Set.of(AdminRole.OWNER));
        repository.save(account);

        assertAccountMatches(repository.findById(account.id()).orElseThrow(), account);
    }

    @Test
    void findByOidcSubjectReturnsAccount() {
        AdminAccount account = account("subject", "owner@example.com", Set.of(AdminRole.OWNER));
        repository.save(account);

        assertAccountMatches(repository.findByOidcSubject(account.oidcSubject()).orElseThrow(), account);
    }

    @Test
    void findByEmailUsesNormalizedEmail() {
        AdminAccount account = account("subject", "Owner@Example.COM", Set.of(AdminRole.OWNER));
        repository.save(account);

        assertAccountMatches(repository.findByEmail(EmailAddress.of("OWNER@example.com")).orElseThrow(), account);
    }

    @Test
    void findByNormalizedEmailReturnsAccount() {
        AdminAccount account = account("subject", "Owner@Example.COM", Set.of(AdminRole.OWNER));
        repository.save(account);

        assertAccountMatches(
                repository.findByNormalizedEmail(NormalizedEmailAddress.of("owner@example.com")).orElseThrow(),
                account);
    }

    @Test
    void saveUpdatesAccountAndRolesWithVersionIncrement() {
        AdminAccount original = account("subject", "owner@example.com", Set.of(AdminRole.OWNER));
        repository.save(original);
        AdminAccount updated = rehydrate(original, Set.of(AdminRole.EDITOR), AdminAccountStatus.ACTIVE, Version.of(1));

        repository.save(updated);

        assertAccountMatches(repository.findById(original.id()).orElseThrow(), updated);
        assertThat(jdbc.queryForList("SELECT role FROM iam.admin_account_roles", String.class))
                .containsExactly("EDITOR");
    }

    @Test
    void savingStaleAccountFails() {
        AdminAccount original = account("subject", "owner@example.com", Set.of(AdminRole.OWNER));
        repository.save(original);
        AdminAccount firstUpdate = original.recordSuccessfulLogin(CREATED_AT.plusSeconds(1));
        AdminAccount staleUpdate = original.recordSuccessfulLogin(CREATED_AT.plusSeconds(2));
        repository.save(firstUpdate);

        assertThatThrownBy(() -> repository.save(staleUpdate))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void existsActiveOwnerReturnsTrueForActiveOwner() {
        repository.save(account("subject", "owner@example.com", Set.of(AdminRole.OWNER)));

        assertThat(repository.existsActiveOwner()).isTrue();
    }

    @Test
    void existsActiveOwnerReturnsFalseForDisabledOwner() {
        repository.save(account("subject", "owner@example.com", Set.of(AdminRole.OWNER)).disable(CREATED_AT.plusSeconds(1)));

        assertThat(repository.existsActiveOwner()).isFalse();
    }

    @Test
    void countAllReturnsNumberOfAccounts() {
        repository.save(account("subject-1", "one@example.com", Set.of(AdminRole.OWNER)));
        repository.save(account("subject-2", "two@example.com", Set.of(AdminRole.EDITOR)));

        assertThat(repository.countAll()).isEqualTo(2);
    }

    @Test
    void duplicateOidcSubjectIsRejectedByDatabase() {
        repository.save(account("duplicate", "one@example.com", Set.of(AdminRole.OWNER)));

        assertThatThrownBy(() -> repository.save(account("duplicate", "two@example.com", Set.of(AdminRole.EDITOR))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateNormalizedEmailIsRejectedByDatabase() {
        repository.save(account("subject-1", "Owner@Example.COM", Set.of(AdminRole.OWNER)));

        assertThatThrownBy(() -> repository.save(account("subject-2", "owner@example.com", Set.of(AdminRole.EDITOR))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static AdminAccount account(String subject, String email, Set<AdminRole> roles) {
        return AdminAccount.create(
                AdminAccountId.newId(),
                OidcSubject.of(subject),
                EmailAddress.of(email),
                DisplayName.of("Admin"),
                roles,
                CREATED_AT);
    }

    private static AdminAccount rehydrate(
            AdminAccount account,
            Set<AdminRole> roles,
            AdminAccountStatus status,
            Version version) {
        return AdminAccount.rehydrate(
                account.id(),
                account.oidcSubject(),
                account.email(),
                account.normalizedEmail(),
                account.displayName(),
                roles,
                status,
                account.lastLoginAt().orElse(null),
                account.createdAt(),
                CREATED_AT.plusSeconds(version.value()),
                version);
    }

    private static void assertAccountMatches(AdminAccount actual, AdminAccount expected) {
        assertThat(actual.id()).isEqualTo(expected.id());
        assertThat(actual.oidcSubject()).isEqualTo(expected.oidcSubject());
        assertThat(actual.email()).isEqualTo(expected.email());
        assertThat(actual.normalizedEmail()).isEqualTo(expected.normalizedEmail());
        assertThat(actual.displayName()).isEqualTo(expected.displayName());
        assertThat(actual.roles()).isEqualTo(expected.roles());
        assertThat(actual.status()).isEqualTo(expected.status());
        assertThat(actual.lastLoginAt()).isEqualTo(expected.lastLoginAt());
        assertThat(actual.createdAt()).isEqualTo(expected.createdAt());
        assertThat(actual.updatedAt()).isEqualTo(expected.updatedAt());
        assertThat(actual.version()).isEqualTo(expected.version());
    }
}
