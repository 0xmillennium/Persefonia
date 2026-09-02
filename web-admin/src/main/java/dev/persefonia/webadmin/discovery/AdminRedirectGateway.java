package dev.persefonia.webadmin.discovery;

import dev.persefonia.discovery.application.authorization.AdminRedirectCommandActor;

public interface AdminRedirectGateway {
    AdminRedirectListResult list();

    AdminRedirectCreateResult create(AdminRedirectCommandActor actor, AdminRedirectForm form);

    AdminRedirectDeactivateResult deactivate(AdminRedirectCommandActor actor, String redirectRuleId);
}
