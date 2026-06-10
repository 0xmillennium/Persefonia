package dev.persefonia.app.security.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import dev.persefonia.identityaccess.application.admin.authorization.AdminCommand;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandActor;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationPolicy;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;

class AdminCommandAuthorizationConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AdminCommandAuthorizationConfiguration.class);

    @Test
    void createsAdminCommandAuthorizationPolicyBean() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(AdminCommandAuthorizationPolicy.class));
    }

    @Test
    void policyBeanAllowsActiveOwner() {
        contextRunner.run(context -> {
            AdminCommandAuthorizationPolicy policy =
                    context.getBean(AdminCommandAuthorizationPolicy.class);

            assertThat(policy.evaluateOwnerRequired(owner(), AdminCommand.named("test.admin.mutate")).isAllowed())
                    .isTrue();
        });
    }

    private static AdminCommandActor owner() {
        return new AdminCommandActor(
                AdminAccountId.of(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                AdminAccountStatus.ACTIVE,
                Set.of(AdminRole.OWNER));
    }
}
