package dev.persefonia.app.identityaccess.bootstrap;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import dev.persefonia.identityaccess.application.admin.bootstrap.AdminBootstrapLock;

@Component
@Lazy
final class PostgresAdminBootstrapLock implements AdminBootstrapLock {
    private final JdbcTemplate jdbcTemplate;

    PostgresAdminBootstrapLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void acquire() {
        jdbcTemplate.execute("LOCK TABLE iam.admin_accounts IN SHARE ROW EXCLUSIVE MODE");
    }
}
