package dev.persefonia.webadmin.discovery;

import org.springframework.security.core.Authentication;

public interface AdminRedirectAccessPolicy {
    void requireOwner(Authentication authentication, String commandName);
}
