package dev.persefonia.app.identityaccess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "persefonia.security.admin-access")
public class AdminAccessProperties {
    private String requiredOidcGroup = "admin";
    private boolean automaticProvisioningEnabled;
    private boolean initialOwnerBootstrapEnabled = true;

    public String getRequiredOidcGroup() {
        return requiredOidcGroup;
    }

    public void setRequiredOidcGroup(String requiredOidcGroup) {
        this.requiredOidcGroup = requiredOidcGroup;
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
