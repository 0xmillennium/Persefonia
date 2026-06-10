package dev.persefonia.app.identityaccess.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.persefonia.identityaccess.domain.admin.AdminAccount;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountRepository;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.NormalizedEmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;
import dev.persefonia.identityaccess.domain.admin.Version;

@Component
@Lazy
final class JdbcAdminAccountRepositoryAdapter implements AdminAccountRepository {
    private static final String SELECT_ACCOUNT = """
            SELECT id, oidc_subject, email, normalized_email, display_name, status,
                   last_login_at, created_at, updated_at, version
            FROM iam.admin_accounts
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcAdminAccountRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AdminAccount save(AdminAccount account) {
        Objects.requireNonNull(account, "account");
        if (existsById(account.id())) {
            update(account);
        } else {
            insert(account);
        }
        return account;
    }

    @Override
    public Optional<AdminAccount> findById(AdminAccountId id) {
        Objects.requireNonNull(id, "id");
        return findOne(SELECT_ACCOUNT + " WHERE id = :id", Map.of("id", id.value()));
    }

    @Override
    public Optional<AdminAccount> findByOidcSubject(OidcSubject oidcSubject) {
        Objects.requireNonNull(oidcSubject, "oidcSubject");
        return findOne(SELECT_ACCOUNT + " WHERE oidc_subject = :oidcSubject", Map.of("oidcSubject", oidcSubject.value()));
    }

    @Override
    public Optional<AdminAccount> findByEmail(EmailAddress email) {
        return findByNormalizedEmail(NormalizedEmailAddress.from(Objects.requireNonNull(email, "email")));
    }

    @Override
    public Optional<AdminAccount> findByNormalizedEmail(NormalizedEmailAddress normalizedEmail) {
        Objects.requireNonNull(normalizedEmail, "normalizedEmail");
        return findOne(
                SELECT_ACCOUNT + " WHERE normalized_email = :normalizedEmail",
                Map.of("normalizedEmail", normalizedEmail.value()));
    }

    @Override
    public boolean existsActiveOwner() {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM iam.admin_accounts a
                    JOIN iam.admin_account_roles r ON r.admin_account_id = a.id
                    WHERE a.status = 'ACTIVE' AND r.role = 'OWNER'
                )
                """, Map.of(), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public long countAll() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM iam.admin_accounts", Map.of(), Long.class);
        return Objects.requireNonNull(count, "count");
    }

    private boolean existsById(AdminAccountId id) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM iam.admin_accounts WHERE id = :id)",
                Map.of("id", id.value()),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private void insert(AdminAccount account) {
        jdbc.update("""
                INSERT INTO iam.admin_accounts (
                    id, oidc_subject, email, normalized_email, display_name, status,
                    last_login_at, created_at, updated_at, version
                ) VALUES (
                    :id, :oidcSubject, :email, :normalizedEmail, :displayName, :status,
                    :lastLoginAt, :createdAt, :updatedAt, :version
                )
                """, accountParameters(account));
        insertRoles(account);
    }

    private void update(AdminAccount account) {
        long expectedPreviousVersion = account.version().value() - 1;
        if (expectedPreviousVersion < 0) {
            throw new OptimisticLockingFailureException("AdminAccount update requires a previous version");
        }

        MapSqlParameterSource parameters = accountParameters(account)
                .addValue("expectedPreviousVersion", expectedPreviousVersion);
        int updated = jdbc.update("""
                UPDATE iam.admin_accounts
                SET oidc_subject = :oidcSubject,
                    email = :email,
                    normalized_email = :normalizedEmail,
                    display_name = :displayName,
                    status = :status,
                    last_login_at = :lastLoginAt,
                    created_at = :createdAt,
                    updated_at = :updatedAt,
                    version = :version
                WHERE id = :id AND version = :expectedPreviousVersion
                """, parameters);
        if (updated == 0) {
            throw new OptimisticLockingFailureException("Stale AdminAccount update");
        }

        jdbc.update("DELETE FROM iam.admin_account_roles WHERE admin_account_id = :id", Map.of("id", account.id().value()));
        insertRoles(account);
    }

    private MapSqlParameterSource accountParameters(AdminAccount account) {
        return new MapSqlParameterSource()
                .addValue("id", account.id().value())
                .addValue("oidcSubject", account.oidcSubject().value())
                .addValue("email", account.email().value())
                .addValue("normalizedEmail", account.normalizedEmail().value())
                .addValue("displayName", account.displayName().value())
                .addValue("status", account.status().name())
                .addValue("lastLoginAt", account.lastLoginAt().map(Timestamp::from).orElse(null))
                .addValue("createdAt", Timestamp.from(account.createdAt()))
                .addValue("updatedAt", Timestamp.from(account.updatedAt()))
                .addValue("version", account.version().value());
    }

    private void insertRoles(AdminAccount account) {
        account.roles().stream()
                .sorted()
                .forEach(role -> jdbc.update("""
                        INSERT INTO iam.admin_account_roles (admin_account_id, role)
                        VALUES (:id, :role)
                        """, Map.of("id", account.id().value(), "role", role.name())));
    }

    private Optional<AdminAccount> findOne(String sql, Map<String, ?> parameters) {
        List<AdminAccount> accounts = jdbc.query(sql, parameters, this::mapAccount);
        return accounts.stream().findFirst();
    }

    private AdminAccount mapAccount(ResultSet resultSet, int rowNumber) throws SQLException {
        AdminAccountId id = AdminAccountId.of(resultSet.getObject("id", java.util.UUID.class));
        return AdminAccount.rehydrate(
                id,
                OidcSubject.of(resultSet.getString("oidc_subject")),
                EmailAddress.of(resultSet.getString("email")),
                NormalizedEmailAddress.of(resultSet.getString("normalized_email")),
                DisplayName.of(resultSet.getString("display_name")),
                loadRoles(id),
                AdminAccountStatus.valueOf(resultSet.getString("status")),
                instantOrNull(resultSet, "last_login_at"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                Version.of(resultSet.getLong("version")));
    }

    private Set<AdminRole> loadRoles(AdminAccountId id) {
        List<AdminRole> roles = jdbc.query(
                "SELECT role FROM iam.admin_account_roles WHERE admin_account_id = :id ORDER BY role",
                Map.of("id", id.value()),
                (resultSet, rowNumber) -> AdminRole.valueOf(resultSet.getString("role")));
        return roles.isEmpty() ? Set.of() : EnumSet.copyOf(roles);
    }

    private static Instant instantOrNull(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
