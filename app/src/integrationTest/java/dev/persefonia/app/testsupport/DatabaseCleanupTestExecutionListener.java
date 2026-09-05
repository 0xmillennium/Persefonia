package dev.persefonia.app.testsupport;

import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/** Clears committed integration data before Spring can open a test-managed transaction. */
public final class DatabaseCleanupTestExecutionListener extends AbstractTestExecutionListener {
    @Override
    public int getOrder() { return 3500; }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        IntegrationDatabaseManager.cleanBeforeTestMethod();
    }
}
