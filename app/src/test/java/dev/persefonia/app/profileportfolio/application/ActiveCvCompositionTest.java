package dev.persefonia.app.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.TestPortfolioSettingsFallbackConfiguration;
import dev.persefonia.profileportfolio.application.port.ActiveCvAssetEligibilityPort;
import dev.persefonia.profileportfolio.application.service.ActiveCvAdminQueryService;
import dev.persefonia.profileportfolio.application.service.ActiveCvCommandGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
@Import(TestPortfolioSettingsFallbackConfiguration.class)
class ActiveCvCompositionTest {
    @Autowired ApplicationContext context;

    @Test
    void fullApplicationCompositionKeepsCvQueriesEligibilityAndTransactionalCommandsAvailable() {
        assertThat(context.getBeansOfType(ActiveCvAssetEligibilityPort.class)).hasSize(1);
        assertThat(context.getBeansOfType(ActiveCvAdminQueryService.class)).hasSize(1);
        assertThat(context.getBeansOfType(ActiveCvCommandGateway.class)).hasSize(1);
    }
}
