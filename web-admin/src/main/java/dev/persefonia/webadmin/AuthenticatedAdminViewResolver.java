package dev.persefonia.webadmin;

import org.springframework.security.core.Authentication;

public interface AuthenticatedAdminViewResolver {
    AuthenticatedAdminView resolve(Authentication authentication);
}
