package dev.persefonia.webadmin.discovery;

public interface AdminRedirectGateway {
    AdminRedirectListResult list();

    AdminRedirectCreateResult create(AdminRedirectForm form);

    AdminRedirectDeactivateResult deactivate(String redirectRuleId);
}
