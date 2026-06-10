package dev.persefonia.app.identityaccess.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "persefonia.security.admin-access")
public class AdminAccessProperties {
    private List<String> allowlistedSubjects = List.of();
    private List<String> allowlistedEmails = List.of();
    private boolean automaticProvisioningEnabled;
    private boolean initialOwnerBootstrapEnabled = true;

    public List<String> getAllowlistedSubjects() {
        return allowlistedSubjects;
    }

    public void setAllowlistedSubjects(List<String> allowlistedSubjects) {
        this.allowlistedSubjects = allowlistedSubjects;
    }

    public List<String> getAllowlistedEmails() {
        return allowlistedEmails;
    }

    public void setAllowlistedEmails(List<String> allowlistedEmails) {
        this.allowlistedEmails = allowlistedEmails;
    }

    public boolean isAutomaticProvisioningEnabled() {
        return automaticProvisioningEnabled;
    }

    public void setAutomaticProvisioningEnabled(boolean automaticProvisioningEnabled) {
        this.automaticProvisioningEnabled = automaticProvisioningEnabled;
    }

    public boolean isInitialOwnerBootstrapEnabled() {
        return initialOwnerBootstrapEnabled;
    }

    public void setInitialOwnerBootstrapEnabled(boolean initialOwnerBootstrapEnabled) {
        this.initialOwnerBootstrapEnabled = initialOwnerBootstrapEnabled;
    }
}
