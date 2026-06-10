package dev.persefonia.app.identityaccess.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessPolicy;
import dev.persefonia.identityaccess.domain.admin.access.AdminIdentityClaims;

class AdminAccessConfigurationTest {
    private final AdminAccessConfiguration configuration = new AdminAccessConfiguration();

    @Test
    void emptyAllowlistCreatesPolicyButAllowsNoIdentity() {
        assertThat(policy(new AdminAccessProperties()).isAllowlisted(claims("subject", "owner@example.com"))).isFalse();
    }

    @Test
    void subjectAllowlistPropertyCreatesAllowlistedPolicy() {
        AdminAccessProperties properties = new AdminAccessProperties();
        properties.setAllowlistedSubjects(List.of("allowed-subject"));

        assertThat(policy(properties).isAllowlisted(claims("allowed-subject", "owner@example.com"))).isTrue();
    }

    @Test
    void emailAllowlistPropertyCreatesNormalizedEmailAllowlistedPolicy() {
        AdminAccessProperties properties = new AdminAccessProperties();
        properties.setAllowlistedEmails(List.of("owner@example.com"));

        assertThat(policy(properties).isAllowlisted(claims("subject", "Owner@Example.COM"))).isTrue();
    }

    @Test
    void blankAllowlistEntriesAreIgnored() {
        AdminAccessProperties properties = new AdminAccessProperties();
        properties.setAllowlistedSubjects(List.of("", "  "));
        properties.setAllowlistedEmails(List.of(" ", "\t"));

        assertThat(policy(properties).isAllowlisted(claims("subject", "owner@example.com"))).isFalse();
    }

    @Test
    void invalidAllowlistEmailFailsConfiguration() {
        AdminAccessProperties properties = new AdminAccessProperties();
        properties.setAllowlistedEmails(List.of("invalid-email"));

        assertThatThrownBy(() -> policy(properties)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void automaticProvisioningDefaultIsFalse() {
        assertThat(policy(new AdminAccessProperties()).automaticProvisioningEnabled()).isFalse();
    }

    @Test
    void initialOwnerBootstrapDefaultIsTrue() {
        assertThat(policy(new AdminAccessProperties()).initialOwnerBootstrapEnabled()).isTrue();
    }

    private AdminAccessPolicy policy(AdminAccessProperties properties) {
        return configuration.adminAccessPolicy(properties);
    }

    private static AdminIdentityClaims claims(String subject, String email) {
        return AdminIdentityClaims.of(
                OidcSubject.of(subject),
                EmailAddress.of(email),
                DisplayName.of("Owner"));
    }
}
