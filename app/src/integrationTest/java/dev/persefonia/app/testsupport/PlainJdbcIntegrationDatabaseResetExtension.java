package dev.persefonia.app.testsupport;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.test.context.SpringBootTest;

/** Applies the shared reset lifecycle to non-Spring integration tests. */
public final class PlainJdbcIntegrationDatabaseResetExtension implements BeforeEachCallback {
    @Override
    public void beforeEach(ExtensionContext context) {
        if (!context.getRequiredTestClass().isAnnotationPresent(SpringBootTest.class)) {
            IntegrationDatabaseManager.cleanBeforeTestMethod();
        }
    }
}
