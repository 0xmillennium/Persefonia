package dev.persefonia.app.identityaccess.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcGroup;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;
import dev.persefonia.identityaccess.domain.admin.access.AdminIdentityClaims;

class AdminAccessConfigurationTest {
    private final AdminAccessConfiguration configuration = new AdminAccessConfiguration();

    @Test
    void requiredGroupRemainsExactAndSwitchesPropagate() {
        AdminAccessProperties properties = new AdminAccessProperties();
        properties.setRequiredOidcGroup("Admin");
        properties.setInitialOwnerBootstrapEnabled(false);
        properties.setAutomaticProvisioningEnabled(true);
        var policy = configuration.adminAccessPolicy(properties);
        assertThat(policy.evaluateAdmission(claims("Admin")).isAllowed()).isTrue();
        assertThat(policy.evaluateAdmission(claims("admin")).isAllowed()).isFalse();
        assertThat(policy.initialOwnerBootstrapEnabled()).isFalse();
        assertThat(policy.automaticProvisioningEnabled()).isTrue();
    }

    @Test
    void conservativeDefaultsAndInvalidConfiguration() {
        AdminAccessProperties properties = new AdminAccessProperties();
        assertThat(configuration.adminAccessPolicy(properties).automaticProvisioningEnabled()).isFalse();
        assertThat(configuration.adminAccessPolicy(properties).initialOwnerBootstrapEnabled()).isTrue();
        for (String value : new String[] {null, " ", "a\nb", "a".repeat(129)}) {
            properties.setRequiredOidcGroup(value);
            assertThatThrownBy(() -> configuration.adminAccessPolicy(properties)).isInstanceOf(RuntimeException.class);
        }
    }

    private static AdminIdentityClaims claims(String group) {
        return AdminIdentityClaims.of(OidcSubject.of("subject"), EmailAddress.of("admin@example.com"),
                DisplayName.of("Admin"), Set.of(OidcGroup.of(group)));
    }
}
